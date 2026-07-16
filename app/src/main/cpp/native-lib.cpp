#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <android/bitmap.h>
#include <cstdio>
#include <cstring>
#include <algorithm>
#include <chrono>
#include <cstdlib>
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include <atomic>

#define LOG_TAG "KASK_NATIVE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::atomic<bool> g_stop_requested(false);

static void run_generation_loop(JNIEnv *env, struct llama_context *ctx, const struct llama_vocab *vocab, struct llama_sampler *smpl, jobject callback, jmethodID onBytesMethod, llama_pos n_past, int max_tokens) {
    const struct llama_model * model = llama_get_model(ctx);
    int32_t n_mtp_heads = 0;
    char meta_buf[16];
    if (llama_model_meta_val_str(model, "llama.n_mtp_heads", meta_buf, sizeof(meta_buf)) > 0 ||
        llama_model_meta_val_str(model, "mtp.n_heads", meta_buf, sizeof(meta_buf)) > 0 ||
        llama_model_meta_val_str(model, "mamba.n_mtp_heads", meta_buf, sizeof(meta_buf)) > 0) {
        try { n_mtp_heads = std::stoi(meta_buf); } catch (...) {}
    }
    n_mtp_heads = std::min(n_mtp_heads, 4);

    LOGI("Starting generation loop. MTP heads: %d, n_past: %ld", n_mtp_heads, (long)n_past);

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onSpeedMethod = env->GetMethodID(callbackClass, "onSpeedUpdate", "(F)V");

    bool has_content_started = false;

    auto send_token = [&](llama_token id) -> bool {
        if (llama_vocab_is_eog(vocab, id)) return false;
        char buf[256];
        int32_t n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            jbyteArray jbytes = env->NewByteArray(n);
            env->SetByteArrayRegion(jbytes, 0, n, (jbyte*)buf);
            env->CallVoidMethod(callback, onBytesMethod, jbytes);
            env->DeleteLocalRef(jbytes);
        }
        return !g_stop_requested;
    };

    auto start_time = std::chrono::high_resolution_clock::now();
    int total_tokens = 0;

    if (n_mtp_heads > 0) {
        for (int i = 0; i < max_tokens; i++) {
            if (g_stop_requested) break;
            llama_token base_id = llama_sampler_sample(smpl, ctx, 0);
            if (!send_token(base_id)) break;
            total_tokens++;

            std::vector<llama_token> drafts;
            for (int k = 0; k < n_mtp_heads; ++k) {
                drafts.push_back(llama_sampler_sample(smpl, ctx, k + 1));
            }
            llama_batch v_batch = llama_batch_init(1 + drafts.size(), 0, 1);
            v_batch.n_tokens = 1 + drafts.size();
            for (int k = 0; k < v_batch.n_tokens; ++k) {
                v_batch.token[k] = (k == 0) ? base_id : drafts[k-1];
                v_batch.pos[k] = n_past + k;
                v_batch.n_seq_id[k] = 1;
                v_batch.seq_id[k][0] = 0;
                v_batch.logits[k] = true;
            }
            if (llama_decode(ctx, v_batch) != 0) {
                llama_batch_free(v_batch);
                break;
            }
            int n_accepted = 1;
            for (size_t k = 0; k < drafts.size(); ++k) {
                llama_token next_id = llama_sampler_sample(smpl, ctx, (int32_t)k * (1 + n_mtp_heads));
                if (next_id == drafts[k]) {
                    if (!send_token(next_id)) {
                        n_accepted = (int)k + 2;
                        n_past += n_accepted;
                        llama_batch_free(v_batch);
                        return;
                    }
                    n_accepted++;
                    total_tokens++;
                } else {
                    break;
                }
            }
            if (n_accepted < v_batch.n_tokens) {
                llama_memory_seq_rm(llama_get_memory(ctx), 0, n_past + n_accepted, -1);
            }
            n_past += n_accepted;
            llama_batch_free(v_batch);
            if (n_accepted > 1) i += (n_accepted - 1);

            auto now = std::chrono::high_resolution_clock::now();
            double elapsed = std::chrono::duration<double>(now - start_time).count();
            if (elapsed > 0.1 && onSpeedMethod) env->CallVoidMethod(callback, onSpeedMethod, (float)(total_tokens / elapsed));
        }
    } else {
        for (int i = 0; i < max_tokens; i++) {
            if (g_stop_requested) break;
            llama_token id = llama_sampler_sample(smpl, ctx, -1);
            if (!send_token(id)) break;
            total_tokens++;

            llama_batch batch = llama_batch_get_one(&id, 1);
            if (llama_decode(ctx, batch) != 0) break;
            n_past++;

            auto now = std::chrono::high_resolution_clock::now();
            double elapsed = std::chrono::duration<double>(now - start_time).count();
            if (elapsed > 0.1 && onSpeedMethod) env->CallVoidMethod(callback, onSpeedMethod, (float)(total_tokens / elapsed));
        }
    }
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_rhuta_kask_domain_engine_LlamaBridge_stop(JNIEnv *env, jobject thiz) {
    g_stop_requested = true;
}

JNIEXPORT jlong JNICALL
Java_com_rhuta_kask_domain_engine_LlamaBridge_loadModel(JNIEnv *env, jobject thiz, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    llama_backend_init();
    llama_model_params mparams = llama_model_default_params();
    mparams.use_mmap = true;
    struct llama_model * model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT jlong JNICALL
Java_com_rhuta_kask_domain_engine_LlamaBridge_createContext(JNIEnv *env, jobject thiz, jlong modelPtr, jint n_ctx, jint n_batch, jboolean use_mtp) {
    if (modelPtr == 0) return 0;
    auto * model = reinterpret_cast<struct llama_model *>(modelPtr);
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx;
    cparams.n_batch = n_batch;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;

    char buf[128];
    int n_mtp_heads = 0;
    if (llama_model_meta_val_str(model, "llama.n_mtp_heads", buf, sizeof(buf)) > 0 ||
        llama_model_meta_val_str(model, "mtp.n_heads", buf, sizeof(buf)) > 0 ||
        llama_model_meta_val_str(model, "mamba.n_mtp_heads", buf, sizeof(buf)) > 0) {
        n_mtp_heads = std::atoi(buf);
    }
    if (use_mtp && n_mtp_heads > 0) {
        cparams.ctx_type = LLAMA_CONTEXT_TYPE_MTP;
    }
    struct llama_context * ctx = llama_init_from_model(model, cparams);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jint JNICALL
Java_com_rhuta_kask_domain_engine_LlamaBridge_getMtpHeads(JNIEnv *env, jobject thiz, jlong modelPtr) {
    if (modelPtr == 0) return 0;
    auto * model = reinterpret_cast<struct llama_model *>(modelPtr);
    char buf[16];
    if (llama_model_meta_val_str(model, "llama.n_mtp_heads", buf, sizeof(buf)) > 0 ||
        llama_model_meta_val_str(model, "mtp.n_heads", buf, sizeof(buf)) > 0 ||
        llama_model_meta_val_str(model, "qwen35.nextn_predict_layers", buf, sizeof(buf)) > 0 ||
        llama_model_meta_val_str(model, "mamba.n_mtp_heads", buf, sizeof(buf)) > 0) {
        try { return std::stoi(buf); } catch (...) { return 0; }
    }
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_rhuta_kask_domain_engine_LlamaBridge_getMarker(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(mtmd_default_marker());
}

JNIEXPORT jstring JNICALL
Java_com_rhuta_kask_domain_engine_LlamaBridge_applyChatTemplate(
        JNIEnv *env, jobject thiz, jlong modelPtr, jobjectArray roles, jobjectArray contents, jboolean addAssistantSlot) {
    if (modelPtr == 0) return env->NewStringUTF("");
    auto * model = reinterpret_cast<struct llama_model *>(modelPtr);
    jsize num_messages = env->GetArrayLength(roles);
    std::vector<llama_chat_message> chat_messages;
    std::vector<std::string> roles_vec;
    std::vector<std::string> contents_vec;
    for (int i = 0; i < num_messages; ++i) {
        jstring role_js = (jstring)env->GetObjectArrayElement(roles, i);
        jstring content_js = (jstring)env->GetObjectArrayElement(contents, i);
        const char * role_cstr = env->GetStringUTFChars(role_js, nullptr);
        const char * content_cstr = env->GetStringUTFChars(content_js, nullptr);
        roles_vec.push_back(std::string(role_cstr));
        contents_vec.push_back(std::string(content_cstr));
        env->ReleaseStringUTFChars(role_js, role_cstr);
        env->ReleaseStringUTFChars(content_js, content_cstr);
        env->DeleteLocalRef(role_js);
        env->DeleteLocalRef(content_js);
    }
    for (size_t i = 0; i < roles_vec.size(); ++i) {
        chat_messages.push_back({ roles_vec[i].c_str(), contents_vec[i].c_str() });
    }
    const char * tmpl = llama_model_chat_template(model, nullptr);
    if (!tmpl || strlen(tmpl) == 0) {
        // Fallback to standard ChatML if model has no template
        tmpl = "{% for message in messages %}{{'<|im_start|>' + message['role'] + '\n' + message['content'] + '<|im_end|>\n'}}{% endfor %}{% if add_generation_prompt %}{{ '<|im_start|>assistant\n' }}{% endif %}";
    }

    std::vector<char> buf(8192);
    int32_t res = llama_chat_apply_template(tmpl, chat_messages.data(), chat_messages.size(), (bool)addAssistantSlot, buf.data(), (int32_t)buf.size());
    if (res > (int32_t)buf.size()) {
        buf.resize(res);
        res = llama_chat_apply_template(tmpl, chat_messages.data(), chat_messages.size(), (bool)addAssistantSlot, buf.data(), (int32_t)buf.size());
    }
    if (res < 0) return env->NewStringUTF("");
    return env->NewStringUTF(std::string(buf.data(), res).c_str());
}

JNIEXPORT void JNICALL
Java_com_rhuta_kask_domain_engine_LlamaBridge_completionStream(JNIEnv *env, jobject thiz, jlong ctxPtr, jstring prompt, jfloat temperature, jobject callback) {
    if (ctxPtr == 0) return;
    g_stop_requested = false;
    auto * ctx = reinterpret_cast<struct llama_context *>(ctxPtr);
    const struct llama_model * model = llama_get_model(ctx);
    const struct llama_vocab * vocab = llama_model_get_vocab(model);
    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    bool add_bos = llama_vocab_get_add_bos(vocab);
    int n_prompt = -llama_tokenize(vocab, prompt_cstr, (int32_t)strlen(prompt_cstr), nullptr, 0, add_bos, true);
    std::vector<llama_token> tokens(n_prompt);
    llama_tokenize(vocab, prompt_cstr, (int32_t)strlen(prompt_cstr), tokens.data(), (int32_t)tokens.size(), add_bos, true);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);
    llama_memory_clear(llama_get_memory(ctx), true);
    llama_pos n_past = 0;
    int32_t n_batch = 512;
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onBytesMethod = env->GetMethodID(callbackClass, "onBytes", "([B)V");
    jmethodID onProgressMethod = env->GetMethodID(callbackClass, "onProgress", "(I)V");

    for (int32_t i = 0; i < (int32_t)tokens.size(); i += n_batch) {
        int32_t n_eval = std::min(n_batch, (int32_t)tokens.size() - i);
        env->CallVoidMethod(callback, onProgressMethod, (int)((float)i / tokens.size() * 100));
        llama_batch batch = llama_batch_init(n_eval, 0, 1);
        batch.n_tokens = n_eval;
        for (int32_t j = 0; j < n_eval; ++j) {
            batch.token[j] = tokens[i + j];
            batch.pos[j] = n_past + j;
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j] = (i + j == (int32_t)tokens.size() - 1);
        }
        if (llama_decode(ctx, batch) != 0) { llama_batch_free(batch); return; }
        n_past += n_eval;
        llama_batch_free(batch);
    }
    env->CallVoidMethod(callback, onProgressMethod, 100);

    auto sparams = llama_sampler_chain_default_params();
    struct llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));
    run_generation_loop(env, ctx, vocab, smpl, callback, onBytesMethod, n_past, 2048);
    llama_sampler_free(smpl);
}

JNIEXPORT void JNICALL Java_com_rhuta_kask_domain_engine_LlamaBridge_freeContext(JNIEnv* env, jobject thiz, jlong ctxPtr) { if (ctxPtr != 0) llama_free(reinterpret_cast<struct llama_context *>(ctxPtr)); }
JNIEXPORT void JNICALL Java_com_rhuta_kask_domain_engine_LlamaBridge_freeModel(JNIEnv* env, jobject thiz, jlong modelPtr) { if (modelPtr != 0) llama_model_free(reinterpret_cast<struct llama_model *>(modelPtr)); }
JNIEXPORT void JNICALL Java_com_rhuta_kask_domain_engine_LlamaBridge_init(JNIEnv *env, jobject thiz, jstring nativeLibDir) { llama_backend_init(); }
JNIEXPORT jint JNICALL Java_com_rhuta_kask_domain_engine_LlamaBridge_getKvCacheTokenCount(JNIEnv *env, jobject thiz, jlong ctxPtr) {
    if (ctxPtr == 0) return 0;
    auto * ctx = reinterpret_cast<struct llama_context *>(ctxPtr);
    llama_memory_t mem = llama_get_memory(ctx);
    return mem ? (jint)(llama_memory_seq_pos_max(mem, 0) + 1) : 0;
}

JNIEXPORT jint JNICALL Java_com_rhuta_kask_domain_engine_LlamaBridge_countTokens(JNIEnv *env, jobject thiz, jlong modelPtr, jstring text) {
    if (modelPtr == 0) return 0;
    auto * model = reinterpret_cast<struct llama_model *>(modelPtr);
    const struct llama_vocab * vocab = llama_model_get_vocab(model);
    const char *prompt_cstr = env->GetStringUTFChars(text, nullptr);
    bool add_bos = llama_vocab_get_add_bos(vocab);
    int n_tokens = -llama_tokenize(vocab, prompt_cstr, (int32_t)strlen(prompt_cstr), nullptr, 0, add_bos, true);
    env->ReleaseStringUTFChars(text, prompt_cstr);
    return (jint)n_tokens;
}

JNIEXPORT jlong JNICALL Java_com_rhuta_kask_domain_engine_LlamaBridge_loadVisionModel(JNIEnv *env, jobject thiz, jstring projectorPath, jlong textModelPtr) {
    if (textModelPtr == 0) return 0;
    const char *path = env->GetStringUTFChars(projectorPath, nullptr);
    auto *model = reinterpret_cast<struct llama_model *>(textModelPtr);
    auto *mctx = mtmd_init_from_file(path, model, mtmd_context_params_default());
    env->ReleaseStringUTFChars(projectorPath, path);
    return reinterpret_cast<jlong>(mctx);
}

JNIEXPORT void JNICALL Java_com_rhuta_kask_domain_engine_LlamaBridge_freeVisionModel(JNIEnv *env, jobject thiz, jlong mtmdPtr) { if (mtmdPtr != 0) mtmd_free(reinterpret_cast<struct mtmd_context *>(mtmdPtr)); }

JNIEXPORT void JNICALL Java_com_rhuta_kask_domain_engine_LlamaBridge_multimodalCompletionStream(JNIEnv *env, jobject thiz, jlong ctxPtr, jlong mtmdPtr, jstring prompt, jfloat temperature, jobject bitmap, jobject callback) {
    if (ctxPtr == 0 || mtmdPtr == 0) return;
    g_stop_requested = false;
    auto *ctx = reinterpret_cast<struct llama_context *>(ctxPtr);
    auto *mctx = reinterpret_cast<struct mtmd_context *>(mtmdPtr);
    const struct llama_vocab *vocab = llama_model_get_vocab(llama_get_model(ctx));

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onBytesMethod = env->GetMethodID(callbackClass, "onBytes", "([B)V");
    jmethodID onProgressMethod = env->GetMethodID(callbackClass, "onProgress", "(I)V");

    mtmd::bitmaps bitmaps;
    if (bitmap != nullptr) {
        AndroidBitmapInfo info; void* pixels;
        if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
        if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;
        std::vector<unsigned char> rgb_data(info.width * info.height * 3);
        auto* src = reinterpret_cast<uint32_t*>(pixels);
        for (uint32_t i = 0; i < info.width * info.height; ++i) {
            rgb_data[i * 3 + 0] = (src[i] >> 16) & 0xFF; rgb_data[i * 3 + 1] = (src[i] >> 8) & 0xFF; rgb_data[i * 3 + 2] = (src[i] >> 0) & 0xFF;
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        bitmaps.entries.emplace_back(mtmd_bitmap_init(info.width, info.height, rgb_data.data()));
    }
    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    mtmd_input_text text_input = { prompt_cstr, llama_vocab_get_add_bos(vocab), true };
    mtmd::input_chunks chunks(mtmd_input_chunks_init());
    auto bitmaps_c_ptr = bitmaps.c_ptr();

    if (mtmd_tokenize(mctx, chunks.ptr.get(), &text_input, bitmaps_c_ptr.data(), bitmaps_c_ptr.size()) != 0) {
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return;
    }

    llama_memory_clear(llama_get_memory(ctx), true);
    llama_pos n_past = 0;
    mtmd_input_chunks * chunks_ptr = chunks.ptr.get();
    size_t n_chunks = mtmd_input_chunks_size(chunks_ptr);

    for (size_t i = 0; i < n_chunks; i++) {
        env->CallVoidMethod(callback, onProgressMethod, (int)((float)i / n_chunks * 100));
        const mtmd_input_chunk * chunk = mtmd_input_chunks_get(chunks_ptr, i);
        mtmd_helper_eval_chunk_single(mctx, ctx, chunk, n_past, 0, 512, (i == n_chunks - 1), &n_past);
    }
    env->CallVoidMethod(callback, onProgressMethod, 100);

    auto sparams = llama_sampler_chain_default_params();
    struct llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));

    run_generation_loop(env, ctx, vocab, smpl, callback, onBytesMethod, n_past, 2048);

    llama_sampler_free(smpl);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);
}

JNIEXPORT void JNICALL Java_com_rhuta_kask_domain_engine_LlamaBridge_transcribeStream(JNIEnv *env, jobject thiz, jlong ctxPtr, jlong mtmdPtr, jbyteArray audioData, jstring prompt, jfloat temperature, jobject callback) {
    if (ctxPtr == 0 || mtmdPtr == 0) return;
    g_stop_requested = false;
    auto *ctx = reinterpret_cast<struct llama_context *>(ctxPtr);
    auto *mctx = reinterpret_cast<struct mtmd_context *>(mtmdPtr);
    const struct llama_vocab *vocab = llama_model_get_vocab(llama_get_model(ctx));

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onBytesMethod = env->GetMethodID(callbackClass, "onBytes", "([B)V");
    jmethodID onProgressMethod = env->GetMethodID(callbackClass, "onProgress", "(I)V");

    jbyte* audio_bytes = env->GetByteArrayElements(audioData, nullptr); jsize audio_len = env->GetArrayLength(audioData);
    mtmd::bitmaps bitmaps;
    auto res = mtmd_helper_bitmap_init_from_buf(mctx, (const unsigned char*)audio_bytes, (size_t)audio_len, false);
    if (res.bitmap) bitmaps.entries.emplace_back(res.bitmap);
    env->ReleaseByteArrayElements(audioData, audio_bytes, JNI_ABORT);

    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    mtmd_input_text text_input = { prompt_cstr, llama_vocab_get_add_bos(vocab), true };
    mtmd::input_chunks chunks(mtmd_input_chunks_init());
    auto bitmaps_c_ptr = bitmaps.c_ptr();

    if (mtmd_tokenize(mctx, chunks.ptr.get(), &text_input, bitmaps_c_ptr.data(), bitmaps_c_ptr.size()) != 0) {
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return;
    }

    llama_memory_clear(llama_get_memory(ctx), true); llama_pos n_past = 0;
    mtmd_input_chunks * chunks_ptr = chunks.ptr.get(); size_t n_chunks = mtmd_input_chunks_size(chunks_ptr);

    for (size_t i = 0; i < n_chunks; i++) {
        env->CallVoidMethod(callback, onProgressMethod, (int)((float)i / n_chunks * 100));
        const mtmd_input_chunk * chunk = mtmd_input_chunks_get(chunks_ptr, i);
        mtmd_helper_eval_chunk_single(mctx, ctx, chunk, n_past, 0, 512, (i == n_chunks - 1), &n_past);
    }
    env->CallVoidMethod(callback, onProgressMethod, 100);

    auto sparams = llama_sampler_chain_default_params(); struct llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature)); llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1)); llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));

    run_generation_loop(env, ctx, vocab, smpl, callback, onBytesMethod, n_past, 1024);

    llama_sampler_free(smpl);
}

}
