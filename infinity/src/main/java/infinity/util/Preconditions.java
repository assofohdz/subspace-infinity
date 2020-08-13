package infinity.util;

import java.text.MessageFormat;

/*
 * Copyright 2015 Olivier Grégoire.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 *
 * @author Olivier Grégoire
 */
public final class Preconditions {

    private Preconditions() {
    }

    public static <T> T checkNotNull(@Nullable final T reference) {
        if (reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }

    public static <T> T checkNotNull(@Nullable final T reference, @Nullable final Object message) {
        if (reference == null) {
            throw new NullPointerException(String.valueOf(message));
        }
        return reference;
    }

    public static <T> T checkNotNull(@Nullable final T reference, final String messageFormat,
            final Object... parameters) {
        if (reference == null) {
            throw new NullPointerException(MessageFormat.format(messageFormat, parameters));
        }
        return reference;
    }

    public static <T> T checkNotNull(@Nullable final T reference, final Supplier<String> messageSupplier) {
        if (reference == null) {
            throw new NullPointerException(messageSupplier == null ? null : messageSupplier.get());
        }
        return reference;
    }

    public static <T> T checkArgument(@Nullable final T value, final Predicate<? super T> predicate) {
        if (!predicate.test(value)) {
            throw new IllegalArgumentException();
        }
        return value;
    }

    public static <T> T checkArgument(@Nullable final T value, final Predicate<? super T> predicate,
            @Nullable final Object message) {
        if (!predicate.test(value)) {
            throw new IllegalArgumentException(String.valueOf(message));
        }
        return value;
    }

    public static <T> T checkArgument(@Nullable final T value, final Predicate<? super T> predicate,
            final String messageFormat, final Object... parameters) {
        if (!predicate.test(value)) {
            throw new IllegalArgumentException(String.format(messageFormat, parameters));
        }
        return value;
    }

    public static <T> T checkArgument(@Nullable final T value, final Predicate<? super T> predicate,
            final Supplier<String> messageSupplier) {
        if (!predicate.test(value)) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
        return value;
    }

    public static void checkArgument(final boolean expression) {
        if (!expression) {
            throw new IllegalArgumentException();
        }
    }

    public static void checkArgument(final boolean expression, @Nullable final Object message) {
        if (!expression) {
            throw new IllegalArgumentException(String.valueOf(message));
        }
    }

    public static void checkArgument(final boolean expression, final String messageFormat, final Object... parameters) {
        if (!expression) {
            throw new IllegalArgumentException(String.format(messageFormat, parameters));
        }
    }

    public static void checkArgument(final boolean expression, final Supplier<String> messageSupplier) {
        if (!expression) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
    }

    public static void checkState(final boolean expression) {
        if (!expression) {
            throw new IllegalStateException();
        }
    }

    public static void checkState(final boolean expression, @Nullable final Object message) {
        if (!expression) {
            throw new IllegalStateException(String.valueOf(message));
        }
    }

    public static void checkState(final boolean expression, final String messageFormat, final Object... parameters) {
        if (!expression) {
            throw new IllegalStateException(String.format(messageFormat, parameters));
        }
    }

    public static void checkState(final boolean expression, final Supplier<String> messageSupplier) {
        if (!expression) {
            throw new IllegalStateException(messageSupplier.get());
        }
    }

    public static int checkElementIndex(final int index, final int size) {
        return checkElementIndex(index, size, "index");
    }

    public static int checkElementIndex(final int index, final int size, @Nullable final String desc) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(badElementIndex(index, size, desc));
        }
        return index;
    }

    private static String badElementIndex(final int index, final int size, final String desc) {
        if (index < 0) {
            return String.format("%d (%d) must not be negative", desc, Integer.valueOf(index));
        } else if (size < 0) {
            throw new IllegalArgumentException("negative size: " + size);
        } else {
            return String.format("%s (%d) must be less than size (%d)", desc, Integer.valueOf(index),
                    Integer.valueOf(size));
        }
    }

    public static int checkPositionIndex(final int index, final int size) {
        return checkPositionIndex(index, size, "index");
    }

    public static int checkPositionIndex(final int index, final int size, @Nullable final String desc) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException(badPositionIndex(index, size, desc));
        }
        return index;
    }

    private static String badPositionIndex(final int index, final int size, final String desc) {
        if (index < 0) {
            return String.format("%s (%d) must not be negative", desc, Integer.valueOf(index));
        } else if (size < 0) {
            throw new IllegalArgumentException("negative size: " + size);
        } else {
            return String.format("%s (%d) must not be greater than size (%d)", desc, Integer.valueOf(index),
                    Integer.valueOf(size));
        }
    }

    public static void checkPositionIndexes(final int start, final int end, final int size) {
        if (start < 0 || end < start || end > size) {
            throw new IndexOutOfBoundsException(badPositionIndexes(start, end, size));
        }
    }

    private static String badPositionIndexes(final int start, final int end, final int size) {
        if (start < 0 || start > size) {
            return badPositionIndex(start, size, "start index");
        }
        if (end < 0 || end > size) {
            return badPositionIndex(end, size, "end index");
        }
        return String.format("end index (%d) must not be less than start index (%d)", Integer.valueOf(end),
                Integer.valueOf(start));
    }

}