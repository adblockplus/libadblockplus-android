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

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import timber.log.Timber;

/**
 * Allowlisted domains adblock fragment.
 * Use the {@link AllowlistedDomainsSettingsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AllowlistedDomainsSettingsFragment
    extends BaseSettingsFragment<AllowlistedDomainsSettingsFragment.Listener>
{
  private SettingsViewModel settingsViewModel;
  private EditText domain;
  private ImageView addDomainButton;
  private ListView listView;
  private Adapter adapter;

  /**
   * Listener with additional `isValidDomain` method
   */
  public interface Listener extends BaseSettingsFragment.Listener
  {
    boolean isValidDomain(AllowlistedDomainsSettingsFragment fragment,
                          String domain, AdblockSettings settings);
  }

  public AllowlistedDomainsSettingsFragment()
  {
    // required empty public constructor
  }

  /**
   * Use this factory method to create a new instance of
   * this fragment using the provided parameters.
   */
  public static AllowlistedDomainsSettingsFragment newInstance()
  {
    return new AllowlistedDomainsSettingsFragment();
  }

  @Override
  public void onAttach(final Activity activity)
  {
    super.onAttach(activity);
    listener = castOrThrow(activity, Listener.class);
  }

  @Override
  public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                           final Bundle savedInstanceState)
  {
    final View rootView = inflater.inflate(
        R.layout.fragment_adblock_allowlisted_domains_settings,
        container,
        false);

    bindControls(rootView);
    checkAndInitControls();

    return rootView;
  }

  @Override
  public void onCreatePreferences(final Bundle bundle, final String key)
  {
    // nothing
  }

  @Override
  protected void onSettingsReady()
  {
    checkAndInitControls();
  }

  @Override
  protected void onAdblockEngineReady()
  {
    checkAndInitControls();
  }

  private void checkAndInitControls()
  {
    // domain != null is used as just as a way to check that the controls are bound.
    if (settings != null && provider != null && domain != null)
    {
      settingsViewModel = new ViewModelProvider(requireActivity(),
          new SettingsViewModelFactory(
              getActivity().getApplication(),
              settings,
              provider)).get(SettingsViewModel.class);
      initControls();
    }
  }

  private void bindControls(final View rootView)
  {
    domain = rootView.findViewById(R.id.fragment_adblock_wl_add_label);
    addDomainButton = rootView.findViewById(R.id.fragment_adblock_wl_add_button);
    listView = rootView.findViewById(R.id.fragment_adblock_wl_listview);
  }

  // Holder for ListView items
  private class Holder
  {
    final TextView domain;
    final ImageView removeButton;

    Holder(final View rootView)
    {
      domain = rootView.findViewById(R.id.fragment_adblock_wl_item_title);
      removeButton = rootView.findViewById(R.id.fragment_adblock_wl_item_remove);
    }
  }

  private final View.OnClickListener removeDomainClickListener = new View.OnClickListener()
  {
    @Override
    public void onClick(final View v)
    {
      // update and save settings
      settingsViewModel.removeDomain((Integer) v.getTag());

      // signal event
      listener.onAdblockSettingsChanged(AllowlistedDomainsSettingsFragment.this);

      // update UI
      adapter.notifyDataSetChanged();
    }
  };

  // Adapter
  private class Adapter extends BaseAdapter
  {
    @Override
    public int getCount()
    {
      return settingsViewModel.getAllowlistedDomainsCount();
    }

    @Override
    public Object getItem(final int position)
    {
      return settingsViewModel.getAllowlistedDomain(position);
    }

    @Override
    public long getItemId(final int position)
    {
      return getItem(position).hashCode();
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent)
    {
      if (convertView == null)
      {
        final LayoutInflater inflater = LayoutInflater.from(getActivity());
        convertView = inflater.inflate(R.layout.fragment_adblock_allowlisted_domain_item,
            parent, false);
        convertView.setTag(new Holder(convertView));
      }

      final String domain = (String) getItem(position);

      final Holder holder = (Holder) convertView.getTag();
      holder.domain.setText(domain);

      holder.removeButton.setOnClickListener(removeDomainClickListener);
      holder.removeButton.setTag(Integer.valueOf(position));

      return convertView;
    }
  }

  private void initControls()
  {
    addDomainButton.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(final View v)
      {
        final String preparedDomain = settingsViewModel.prepareDomain(
            domain.getText().toString());

        if (listener.isValidDomain(
            AllowlistedDomainsSettingsFragment.this,
            preparedDomain,
            settings))
        {
          if (settingsViewModel.addDomain(preparedDomain))
          {
            // signal event
            listener.onAdblockSettingsChanged(AllowlistedDomainsSettingsFragment.this);

            // update UI
            adapter.notifyDataSetChanged();
          }
        }
        else
        {
          Timber.w("Domain " + preparedDomain + " is not valid");
        }

        domain.getText().clear();
        domain.clearFocus();
      }
    });

    adapter = new Adapter();
    listView.setAdapter(adapter);
  }

}
