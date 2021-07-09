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

package org.adblockplus;

import org.jetbrains.annotations.NotNull;

/**
 * Data class which identifies the application when downloading {@link Subscription}s.
 *
 * In most cases it is recommended to NOT create {@link AppInfo} object just pass a null value to
 * {@link AdblockEngineFactory#getAdblockEngineBuilder} so the correct {@link AppInfo} object is generated
 * automatically.
 */
public class AppInfo
{
  private final String version = "1.0";
  private final String name = "adblockplus-android";
  private final String application;
  private final String applicationVersion;
  private final String locale;

  private AppInfo(@NotNull final String application, @NotNull final String applicationVersion,
                  @NotNull final String locale)
  {
    this.application = application;
    this.applicationVersion = applicationVersion;
    this.locale = locale;
  }

  /**
   * Creates {@link AppInfo.Builder} object.
   * @return {@link Builder} object.
   */
  @NotNull
  public static Builder builder()
  {
    return new Builder();
  }

  /**
   * {@link AppInfo} builder class.
   */
  public static class Builder
  {
    private String application = "android";
    private String applicationVersion = "0";
    private String locale = "en-US";

    private Builder()
    {
    }

    /**
     * Sets application name.
     * @param application application name
     * @return {@link Builder} to allow chaining
     */
    public Builder setApplication(@NotNull final String application)
    {
      this.application = application;
      return this;
    }

    /**
     * Sets application version.
     * @param applicationVersion application version
     * @return {@link Builder} to allow chaining
     */
    public Builder setApplicationVersion(@NotNull final String applicationVersion)
    {
      this.applicationVersion = applicationVersion;
      return this;
    }

    /**
     * Sets application locale.
     * @param locale application locale
     * @return {@link Builder} to allow chaining
     */
    public Builder setLocale(@NotNull final String locale)
    {
      this.locale = locale;
      return this;
    }

    /**
     * Builds the {@link AppInfo} object.
     * @note: In most cases it is recommended to NOT create {@link AppInfo} object just pass a null value to
     * {@link AdblockEngineFactory#getAdblockEngineBuilder} so the correct {@link AppInfo} object is generated
     * automatically.
     * @return {@link AppInfo} object
     */
    public AppInfo build()
    {
      return new AppInfo(this.application, this.applicationVersion, this.locale);
    }
  }
}
