package ai.driverhelper.voice

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface VoiceModule {
    @Binds
    fun voiceGuide(impl: PrerecordedVoiceGuide): VoiceGuide
}
