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

package org.adblockplus.libadblockplus;

import java.nio.ByteBuffer;

public abstract class FileSystem
{
  /**
   * Result of a stat operation, i.e. information about a file.
   */
  public static class StatResult
  {
    private boolean exists;
    private long modified;

    public StatResult(final boolean exists, final long modified)
    {
      this.exists = exists;
      this.modified = modified;
    }

    public boolean isExists()
    {
      return exists;
    }

    public long getModified()
    {
      return modified;
    }
  }

  /**
   * Default callback type for asynchronous filesystem calls.
   */
  public static class Callback implements Disposable
  {
    protected final long ptr;
    private final Disposer disposer;

    Callback(final long ptr)
    {
      this.ptr = ptr;
      this.disposer = new Disposer(this, new DisposeWrapper(this.ptr));
    }

    private static final class DisposeWrapper implements Disposable
    {
      private final long ptr;

      public DisposeWrapper(final long ptr)
      {
        this.ptr = ptr;
      }

      @Override
      public void dispose()
      {
      }
    }

    @Override
    public void dispose()
    {
      this.disposer.dispose();
    }

    /**
     * @param error An error string. Empty is success.
     */
    public void onFinished(final String error)
    {
    }
  }

  /**
   * Callback type for the asynchronous Read call.
   */
  public static class ReadCallback implements Disposable
  {
    protected final long ptr;
    private final Disposer disposer;

    ReadCallback(final long ptr)
    {
      this.ptr = ptr;
      this.disposer = new Disposer(this, new DisposeWrapper(this.ptr));
    }

    private static final class DisposeWrapper implements Disposable
    {
      private final long ptr;

      public DisposeWrapper(final long ptr)
      {
        this.ptr = ptr;
      }

      @Override
      public void dispose()
      {
      }
    }

    @Override
    public void dispose()
    {
      this.disposer.dispose();
    }

    /**
     * @param output char array with file content,
     *               (*direct* buffer, allocated with `ByteBuffer.allocateDirect`)
     */
    public void onFinished(final ByteBuffer output)
    {
    }
  }

  /**
   * Callback type for the asynchronous Stat call.
   */
  public static class StatCallback implements Disposable
  {
    protected final long ptr;
    private final Disposer disposer;

    StatCallback(final long ptr)
    {
      this.ptr = ptr;
      this.disposer = new Disposer(this, new DisposeWrapper(this.ptr));
    }

    private static final class DisposeWrapper implements Disposable
    {
      private final long ptr;

      public DisposeWrapper(final long ptr)
      {
        this.ptr = ptr;
      }

      @Override
      public void dispose()
      {
      }
    }

    @Override
    public void dispose()
    {
      this.disposer.dispose();
    }

    /**
     * @param result StatResult data.
     * @param error error string. `Null` if no error.
     */
    public void onFinished(final StatResult result, final String error)
    {
    }
  }

  /**
   * Reads from a file.
   * @param filename File name.
   * @param doneCallback The callback called on completion with the input data.
   * @param errorCallback The callback called if an error occurred.
   */
  public abstract void read(final String filename,
                            final ReadCallback doneCallback,
                            final Callback errorCallback);

  /**
   * Writes to a file.
   * @param filename File name.
   * @param data The data to write, *direct* buffer (allocated with `env-&gt;NewDirectByteBuffer()`)
   * @param callback The callback called on completion.
   */
  public abstract void write(final String filename,
                             final ByteBuffer data,
                             final Callback callback);

  /**
   * Moves a file (i.e. renames it).
   * @param fromFilename Current file name.
   * @param toFilename New file name.
   * @param callback The callback called on completion.
   */
  public abstract void move(final String fromFilename,
                            final String toFilename,
                            final Callback callback);

  /**
   * Removes a file.
   * @param filename File name.
   * @param callback The callback called on completion.
   */
  public abstract void remove(final String filename,
                              final Callback callback);

  /**
   * Retrieves information about a file.
   * @param filename File name.
   * @param callback The callback called on completion.
   */
  public abstract void stat(final String filename,
                            final StatCallback callback);

}
