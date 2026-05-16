// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

/** Per-arena winCondition vote at round end. {@code winningFreq == -1} signals UNDECIDED (abstain). */
public record WinnerDeclaration(int winningFreq, String reason) {

  public static final WinnerDeclaration UNDECIDED = new WinnerDeclaration(-1, "");
}
