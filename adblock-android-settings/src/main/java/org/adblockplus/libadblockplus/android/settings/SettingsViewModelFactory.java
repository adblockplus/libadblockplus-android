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

package org.adblockplus.libadblockplus.android.settings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.adblockplus.libadblockplus.android.AdblockEngine;

public class SettingsViewModelFactory implements ViewModelProvider.Factory
{
  final private Application application;
  final private AdblockSettings settings;
  final private AdblockEngine engine;
  final private BaseSettingsFragment.Provider provider;

  public SettingsViewModelFactory(final Application application,
                                  final AdblockSettings settings,
                                  final AdblockEngine engine,
                                  final BaseSettingsFragment.Provider provider)
  {
    this.application = application;
    this.settings = settings;
    this.engine = engine;
    this.provider = provider;
  }

  @NonNull
  @Override
  public <T extends ViewModel> T create(@NonNull final Class<T> modelClass)
  {
    return (T) new SettingsViewModel(application, settings, engine, provider);
  }
}
