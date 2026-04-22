/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.settings;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import org.ini4j.Ini;

/**
 * Asset loader for {@code .ini}/{@code .cfg}/{@code .conf} files with a lightweight {@code
 * #include} preprocessor so arena configs can share common fragments.
 *
 * <p>Supported directive only: {@code #include <path>} (optionally quoted). Paths starting with
 * {@code /} are resolved against the asset root; otherwise they are resolved relative to the
 * directory of the including file. Cycles and excessive nesting raise {@link IOException} with
 * file:line context.
 *
 * @author Asser Fahrenholz
 */
public class IniLoader implements AssetLoader {

  private static final int MAX_INCLUDE_DEPTH = 16;
  private static final String INCLUDE_DIRECTIVE = "#include";

  @Override
  public Ini load(final AssetInfo assetInfo) throws IOException {
    final String rootPath = assetInfo.getKey().getName();
    final StringBuilder out = new StringBuilder();
    try (InputStream is = assetInfo.openStream()) {
      expand(assetInfo.getManager(), rootPath, is, out, new ArrayDeque<>(), 0);
    }
    return new Ini(new StringReader(out.toString()));
  }

  private void expand(
      final AssetManager am,
      final String path,
      final InputStream stream,
      final StringBuilder out,
      final Deque<String> stack,
      final int depth)
      throws IOException {
    if (depth > MAX_INCLUDE_DEPTH) {
      throw new IOException(
          "#include nesting exceeded max depth " + MAX_INCLUDE_DEPTH + " at " + path);
    }
    if (stack.contains(path)) {
      throw new IOException("#include cycle detected: " + String.join(" -> ", stack) + " -> " + path);
    }
    stack.push(path);
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      int lineNo = 0;
      while ((line = reader.readLine()) != null) {
        lineNo++;
        if (isIncludeLine(line)) {
          final String target = parseIncludeTarget(line);
          if (target.isEmpty()) {
            throw new IOException(path + ":" + lineNo + " — #include missing path");
          }
          final String resolved = resolvePath(path, target);
          final AssetInfo included = am.locateAsset(new AssetKey<>(resolved));
          if (included == null) {
            throw new IOException(
                path + ":" + lineNo + " — #include target not found: " + resolved);
          }
          try (InputStream is = included.openStream()) {
            expand(am, resolved, is, out, stack, depth + 1);
          }
        } else {
          out.append(line).append('\n');
        }
      }
    }
    stack.pop();
  }

  private static boolean isIncludeLine(final String line) {
    final String trimmed = line.stripLeading();
    if (!trimmed.startsWith(INCLUDE_DIRECTIVE)) {
      return false;
    }
    // Guard against keys that happen to start with "#include" — next char must be whitespace.
    if (trimmed.length() == INCLUDE_DIRECTIVE.length()) {
      return true;
    }
    return Character.isWhitespace(trimmed.charAt(INCLUDE_DIRECTIVE.length()));
  }

  private static String parseIncludeTarget(final String line) {
    String arg = line.stripLeading().substring(INCLUDE_DIRECTIVE.length()).trim();
    if (arg.length() >= 2
        && (arg.charAt(0) == '"' || arg.charAt(0) == '\'')
        && arg.charAt(arg.length() - 1) == arg.charAt(0)) {
      arg = arg.substring(1, arg.length() - 1);
    }
    return arg;
  }

  private static String resolvePath(final String includer, final String target) {
    final String base;
    if (target.startsWith("/")) {
      base = target;
    } else {
      final int lastSlash = includer.lastIndexOf('/');
      final String parent = lastSlash >= 0 ? includer.substring(0, lastSlash) : "";
      base = parent + "/" + target;
    }
    return Paths.get(base).normalize().toString().replace(File.separatorChar, '/');
  }
}
