package infinity.es.states.tools;

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

  public static <T> T checkNotNull(@Nullable T reference) {
    if (reference != null) {
      return reference;
    } else {
      throw new NullPointerException();
    }
  }

  public static <T> T checkNotNull(@Nullable T reference, @Nullable Object message) {
    if (reference != null) {
      return reference;
    } else {
      throw new NullPointerException(String.valueOf(message));
    }
  }

  public static <T> T checkNotNull(@Nullable T reference, String messageFormat, Object... parameters) {
    if (reference != null) {
      return reference;
    } else {
      throw new NullPointerException(String.format(messageFormat, parameters));
    }
  }

  public static <T> T checkNotNull(@Nullable T reference, Supplier<String> messageSupplier) {
    if (reference != null) {
      return reference;
    } else {
      throw new NullPointerException(messageSupplier.get());
    }
  }

  public static <T> T checkArgument(@Nullable T value, Predicate<? super T> predicate) {
    if (predicate.test(value)) {
      return value;
    } else {
      throw new IllegalArgumentException();
    }
  }

  public static <T> T checkArgument(@Nullable T value, Predicate<? super T> predicate, @Nullable Object message) {
    if (predicate.test(value)) {
      return value;
    } else {
      throw new IllegalArgumentException(String.valueOf(message));
    }
  }

  public static <T> T checkArgument(@Nullable T value, Predicate<? super T> predicate, String messageFormat, Object... parameters) {
    if (predicate.test(value)) {
      return value;
    } else {
      throw new IllegalArgumentException(String.format(messageFormat, parameters));
    }
  }

  public static <T> T checkArgument(@Nullable T value, Predicate<? super T> predicate, Supplier<String> messageSupplier) {
    if (predicate.test(value)) {
      return value;
    } else {
      throw new IllegalArgumentException(messageSupplier.get());
    }
  }

  public static void checkArgument(boolean expression) {
    if (!expression) {
      throw new IllegalArgumentException();
    }
  }

  public static void checkArgument(boolean expression, @Nullable Object message) {
    if (!expression) {
      throw new IllegalArgumentException(String.valueOf(message));
    }
  }

  public static void checkArgument(boolean expression, String messageFormat, Object... parameters) {
    if (!expression) {
      throw new IllegalArgumentException(String.format(messageFormat, parameters));
    }
  }

  public static void checkArgument(boolean expression, Supplier<String> messageSupplier) {
    if (!expression) {
      throw new IllegalArgumentException(messageSupplier.get());
    }
  }

  public static void checkState(boolean expression) {
    if (!expression) {
      throw new IllegalStateException();
    }
  }

  public static void checkState(boolean expression, @Nullable Object message) {
    if (!expression) {
      throw new IllegalStateException(String.valueOf(message));
    }
  }

  public static void checkState(boolean expression, String messageFormat, Object... parameters) {
    if (!expression) {
      throw new IllegalStateException(String.format(messageFormat, parameters));
    }
  }

  public static void checkState(boolean expression, Supplier<String> messageSupplier) {
    if (!expression) {
      throw new IllegalStateException(messageSupplier.get());
    }
  }

  public static int checkElementIndex(int index, int size) {
    return checkElementIndex(index, size, "index");
  }

  public static int checkElementIndex(int index, int size, @Nullable String desc) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(badElementIndex(index, size, desc));
    }
    return index;
  }

  private static String badElementIndex(int index, int size, String desc) {
    if (index < 0) {
      return String.format("%d (%d) must not be negative", desc, index);
    } else if (size < 0) {
      throw new IllegalArgumentException("negative size: " + size);
    } else {
      return String.format("%s (%d) must be less than size (%d)", desc, index, size);
    }
  }

  public static int checkPositionIndex(int index, int size) {
    return checkPositionIndex(index, size, "index");
  }

  public static int checkPositionIndex(int index, int size, @Nullable String desc) {
    if (index < 0 || index > size) {
      throw new IndexOutOfBoundsException(badPositionIndex(index, size, desc));
    }
    return index;
  }

  private static String badPositionIndex(int index, int size, String desc) {
    if (index < 0) {
      return String.format("%s (%d) must not be negative", desc, index);
    } else if (size < 0) {
      throw new IllegalArgumentException("negative size: " + size);
    } else {
      return String.format("%s (%d) must not be greater than size (%d)", desc, index, size);
    }
  }

  public static void checkPositionIndexes(int start, int end, int size) {
    if (start < 0 || end < start || end > size) {
      throw new IndexOutOfBoundsException(badPositionIndexes(start, end, size));
    }
  }

  private static String badPositionIndexes(int start, int end, int size) {
    if (start < 0 || start > size) {
      return badPositionIndex(start, size, "start index");
    }
    if (end < 0 || end > size) {
      return badPositionIndex(end, size, "end index");
    }
    return String.format("end index (%d) must not be less than start index (%d)", end, start);
  }

}