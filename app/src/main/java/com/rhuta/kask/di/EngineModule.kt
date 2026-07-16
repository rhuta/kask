package com.rhuta.kask.di

import com.rhuta.kask.domain.audio.AudioRecorder
import com.rhuta.kask.domain.engine.AIEngine
import com.rhuta.kask.domain.engine.LlamaCppEngine
import com.rhuta.kask.domain.engine.StubAIEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindAIEngine(engine: LlamaCppEngine): AIEngine

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        @Provides
        @Singleton
        fun provideAudioRecorder(@ApplicationContext context: android.content.Context): AudioRecorder {
            return AudioRecorder(context)
        }
    }
}
