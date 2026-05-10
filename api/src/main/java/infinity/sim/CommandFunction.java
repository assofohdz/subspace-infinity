// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import java.util.function.Function;

/**
 * One-argument chat-command handler with a required access level.
 *
 * @author Asser
 */
public class CommandFunction<T, R> {

  private final AccessLevel accessLevelRequired;
  private final Function<T, R> consumer;

  public CommandFunction(final AccessLevel accessLevelRequired, Function<T, R> consumer) {
    this.accessLevelRequired = accessLevelRequired;
    this.consumer = consumer;
  }

  public AccessLevel getAccessLevelRequired() {
    return accessLevelRequired;
  }

  public Function<T, R> getConsumer() {
    return consumer;
  }
}
