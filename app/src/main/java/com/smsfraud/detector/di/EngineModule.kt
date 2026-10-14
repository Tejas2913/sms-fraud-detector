package com.smsfraud.detector.di

import android.content.Context
import com.smsfraud.detector.domain.engine.DecisionController
import com.smsfraud.detector.domain.engine.MlEngine
import com.smsfraud.detector.domain.engine.RuleEngine
import com.smsfraud.detector.domain.engine.ScoreFusion
import com.smsfraud.detector.domain.engine.SenderContextEngine
import com.smsfraud.detector.domain.engine.UrlEngine
import com.smsfraud.detector.engine.DecisionControllerImpl
import com.smsfraud.detector.engine.MlEngineImpl
import com.smsfraud.detector.engine.RuleEngineImpl
import com.smsfraud.detector.engine.ScoreFusionImpl
import com.smsfraud.detector.engine.SenderContextEngineImpl
import com.smsfraud.detector.engine.UrlEngineImpl
import com.smsfraud.detector.preprocessing.TextPreprocessor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI bindings for Milestone 2 engine implementations.
 *
 * Interfaces → implementations:
 *   RuleEngine         → RuleEngineImpl
 *   UrlEngine          → UrlEngineImpl
 *   SenderContextEngine→ SenderContextEngineImpl
 *   MlEngine           → MlEngineImpl
 *   ScoreFusion        → ScoreFusionImpl
 *   DecisionController → DecisionControllerImpl
 *
 * TextPreprocessor is provided via @Provides since it requires Context
 * and uses a static factory method rather than @Inject constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds @Singleton
    abstract fun bindRuleEngine(impl: RuleEngineImpl): RuleEngine

    @Binds @Singleton
    abstract fun bindUrlEngine(impl: UrlEngineImpl): UrlEngine

    @Binds @Singleton
    abstract fun bindSenderEngine(impl: SenderContextEngineImpl): SenderContextEngine

    @Binds @Singleton
    abstract fun bindMlEngine(impl: MlEngineImpl): MlEngine

    @Binds @Singleton
    abstract fun bindScoreFusion(impl: ScoreFusionImpl): ScoreFusion

    @Binds @Singleton
    abstract fun bindDecisionController(impl: DecisionControllerImpl): DecisionController

    companion object {
        @Provides
        @Singleton
        fun provideTextPreprocessor(@ApplicationContext context: Context): TextPreprocessor {
            return TextPreprocessor.fromAssets(context)
        }
    }
}
