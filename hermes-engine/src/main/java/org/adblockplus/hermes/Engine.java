/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-present eyeo GmbH
 *
 * Adblock Plus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * Adblock Plus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adblock Plus.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.adblockplus.hermes;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.facebook.soloader.nativeloader.NativeLoader;
import com.facebook.soloader.nativeloader.SystemDelegate;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressWarnings("JavaJniMissingFunction")
public class Engine implements AdblockEngine
{
  public static final String API_HBC = "API.hbc";
  @SuppressWarnings({"unused", "FieldMayBeFinal"})
  // used in native
  private long nativePtr = 0L;
  private final EngineScheduler looper = new EngineScheduler();

  private static final String DIR_AND_TAG = "Hermes";
  private static final String SUBSCRIPTIONS_IN = "patterns_mini.ini";
  private static final String SUBSCRIPTIONS_OUT = "patterns.ini";

  private static void createDirectoryIfNeeded(final File directory)
  {
    if (!directory.exists())
    {
      Log.i(DIR_AND_TAG, "Creating dir " + directory);
      if (!directory.mkdirs())
      {
        Log.w(DIR_AND_TAG, "Failed creating dir " + directory);
      }
    }
  }

  private static void copyAssetToFile(final AssetManager assetManager,
                                      final String inAssetName,
                                      final File outFile)
  {
    try
    {
      Log.i(DIR_AND_TAG, "Creating file " + outFile);
      //noinspection ResultOfMethodCallIgnored
      outFile.createNewFile();
      final InputStream in = assetManager.open(inAssetName);
      final OutputStream out = new FileOutputStream(outFile);

      final byte[] buffer = new byte[1024];
      int read;
      while((read = in.read(buffer)) != -1)
      {
        out.write(buffer, 0, read);
      }

      in.close();
      out.flush();
      out.close();
    }
    catch(final IOException e)
    {
      Log.e(DIR_AND_TAG, "Failed to copy asset file: " + inAssetName, e);
    }
  }

  public Engine(@NonNull final Context context)
  {
    final String coreStoragePath = context.getDir("adblock", Context.MODE_PRIVATE).getAbsolutePath();
    final File coreCodeDir = new File(context.getCacheDir(), DIR_AND_TAG);
    createDirectoryIfNeeded(coreCodeDir);
    Log.i(DIR_AND_TAG, "Subscriptions data dir is: " + coreStoragePath);
    copyAssetToFile(context.getAssets(), SUBSCRIPTIONS_IN, new File(coreStoragePath, SUBSCRIPTIONS_OUT));
    final File apiPath = new File(coreStoragePath, API_HBC);
    copyAssetToFile(context.getAssets(), API_HBC, apiPath);
    Log.d(DIR_AND_TAG, "Init starts");
    init(coreStoragePath, apiPath.getAbsolutePath());
    Log.d(DIR_AND_TAG, "Init ends");
  }

  private native void init(String baseDataFolder, String coreJsFilePath);

  /**
   * This method if visible to test the javascript engine functionality
   *
   * @return a string representing the evaluation result
   */
  @VisibleForTesting
  native String evaluateJS(final String src);

  static
  {
    NativeLoader.init(new SystemDelegate());
    NativeLoader.loadLibrary("engine");
  }


  @Override
  public @NotNull String getElementHidingStyleSheet(@NotNull final String domain,
                                                    final boolean specificOnly)
  {
    return _getElementHidingStyleSheet(domain, specificOnly);
  }

  @Override
  public @NotNull List<EmulationSelector> getElementHidingEmulationSelectors(
      @NotNull final String domain)
  {
    final ArrayList<EmulationSelector> result = new ArrayList<>();
    for (final Object object : _getElementHidingEmulationSelectors(domain))
    {
      if (object instanceof EmulationSelector)
      {
        result.add((EmulationSelector) object);
      }
    }
    return result;
  }

  @Override
  public boolean isContentAllowlisted(@NotNull final String url,
                                      @NotNull final Set<ContentType> contentTypes,
                                      @NotNull final List<String> referrerChain,
                                      @NotNull final String siteKey)
  {
    return _isContentAllowlisted(ContentType.getCombinedValue(contentTypes), referrerChain,
        siteKey);
  }

  @Override
  public MatchesResult matches(@NotNull final String url,
                               @NotNull final Set<ContentType> contentTypes,
                               @NotNull final String parent, @NotNull final String siteKey,
                               final boolean domainSpecificOnly)
  {
    final String matchResult = _matches(url, ContentType.getCombinedValue(contentTypes),
        parent, siteKey, domainSpecificOnly);
    switch (matchResult)
    {
      case "allowing":
        return MatchesResult.ALLOWLISTED;
      case "blocking":
        return MatchesResult.BLOCKED;
      default:
        return MatchesResult.NOT_FOUND;
    }
///@TODO    MatchesResult.NOT_ENABLED;
  }

  @Override
  public @NotNull Subscription getSubscription(@NotNull final String url)
  {
    throw new RuntimeException("Not implemented yet");
  }

  @Override
  public @NotNull Filter getFilterFromText(@NotNull final String text)
  {
    throw new RuntimeException("Not implemented yet");
  }

  private void schedule(@NonNull final JSFunctionWrapper jsFunction, final long millis)
  {
    looper.post(this, jsFunction, millis);
  }

  private native String _matches(String url, int contentTypeMask,
                                 String parent, String siteKey, boolean domainSpecificOnly);

  private native boolean _isContentAllowlisted(int contentTypeMask, List<String> referrerChain,
                                               String siteKey);

  private native String _getElementHidingStyleSheet(String domain, boolean specificOnly);

  private native Object[] _getElementHidingEmulationSelectors(String domain);

  native void _executeJSFunction(@NonNull final JSFunctionWrapper jsFunction);
}
