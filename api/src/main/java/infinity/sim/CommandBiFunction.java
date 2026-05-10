// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import java.util.function.BiFunction;

/**
 * Two-argument chat-command handler with a required access level.
 *
 * @author Asser
 */
public class CommandBiFunction<T, U, R>{

  private final AccessLevel accessLevelRequired;
  private final BiFunction<T, U, R> consumer;

  public CommandBiFunction(
      final AccessLevel accessLevelRequired, final BiFunction<T, U, R> consumer) {
    this.accessLevelRequired = accessLevelRequired;
    this.consumer = consumer;
  }

  public AccessLevel getAccessLevelRequired() {
    return accessLevelRequired;
  }

  public BiFunction<T, U, R> getConsumer() {
    return consumer;
  }
}
