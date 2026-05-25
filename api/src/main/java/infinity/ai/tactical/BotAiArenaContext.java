// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.field.NavigationFields;

/**
 * Per-arena bot-AI services a brain holds a reference to. Slice #02 exposes navigation;
 * spatial-field + capability-norm accessors (ADR-0012 / ADR-0014) are added by their slices.
 * See ADR-0012.
 */
public interface BotAiArenaContext {

  NavigationFields navigation();
}
