package com.mulesoft.connectors.typesafe.internal.connection;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.api.store.ObjectStoreManager;
import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.Parameter;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.connectivity.CachedConnectionProvider;
import org.mule.sdk.api.connectivity.ConnectionValidationResult;

import com.mulesoft.connectors.typesafe.internal.cache.DecisionCache;
import com.mulesoft.connectors.typesafe.internal.engine.BudgetGuard;
import com.mulesoft.connectors.typesafe.internal.engine.DecisionEngine;
import com.mulesoft.connectors.typesafe.internal.engine.DelayScheduler;
import com.mulesoft.connectors.typesafe.internal.engine.RetryPolicy;
import com.mulesoft.connectors.typesafe.internal.provider.MockAdapter;
import com.mulesoft.connectors.typesafe.internal.stats.DecisionStatsRecorder;

import java.util.List;

import javax.inject.Inject;

/**
 * Keyless connection provider that answers from in-process fixtures. It lets tests, the demo app and design-time
 * tooling exercise every operation without a provider key. It holds no transport, so it does not share the HTTP client
 * lifecycle of the keyed providers, but it does own the shared decision engine (and its runtime retry scheduler) the
 * same way.
 */
@Alias("mock")
@DisplayName("Mock (testing)")
public class MockConnectionProvider
    implements
      CachedConnectionProvider<TypeSafeConnection>,
      org.mule.runtime.api.lifecycle.Startable,
      org.mule.runtime.api.lifecycle.Stoppable {

  @Inject
  private SchedulerService schedulerService;

  @Inject
  private ObjectStoreManager objectStoreManager;

  private Scheduler scheduler;
  private DecisionEngine engine;
  private DecisionCache cache;
  private BudgetGuard budget;
  private DecisionStatsRecorder stats;

  @Parameter
  @Optional
  @Summary("Classpath folder holding canned JSON decision responses.")
  private String fixturesLocation;

  @Parameter
  @Optional(defaultValue = "0.5")
  @Summary("Noul probability returned when a fixture does not specify one.")
  private double defaultNoul;

  @Parameter
  @Optional(defaultValue = "0")
  @Summary("Artificial latency in milliseconds, to exercise timeout and retry paths.")
  private long latencyMs;

  @Override
  public void start() {
    scheduler = schedulerService.cpuLightScheduler();
    engine = new DecisionEngine(new RetryPolicy(), DelayScheduler.on(scheduler));
    cache = DecisionCache.create(objectStoreManager);
    budget = BudgetGuard.create(objectStoreManager);
    stats = DecisionStatsRecorder.create(objectStoreManager);
  }

  @Override
  public void stop() {
    if (scheduler != null) {
      scheduler.stop();
    }
  }

  @Override
  public TypeSafeConnection connect() {
    return new TypeSafeConnection(new MockAdapter(defaultNoul, latencyMs), List.of(), engine, cache, budget, stats);
  }

  @Override
  public void disconnect(TypeSafeConnection connection) {
    // Nothing to release: the mock route holds no transport resources.
  }

  @Override
  public ConnectionValidationResult validate(TypeSafeConnection connection) {
    return ConnectionValidationResult.success();
  }
}
