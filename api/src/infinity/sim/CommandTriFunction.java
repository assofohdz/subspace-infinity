// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

/**
 * Three-argument chat-command handler with a required access level.
 *
 * @author Asser
 */
public class CommandTriFunction<T, U, V, R> {

  private final AccessLevel accessLevelRequired;
  private final TriFunction<T, U, V, R> function;

  public CommandTriFunction(
      final AccessLevel accessLevelRequired, final TriFunction<T, U, V, R> function) {
    this.accessLevelRequired = accessLevelRequired;
    this.function = function;
  }

  public AccessLevel getAccessLevelRequired() {
    return accessLevelRequired;
  }

  public TriFunction<T, U, V, R> getFunction() {
    return function;
  }
}
