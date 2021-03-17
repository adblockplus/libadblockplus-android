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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * All the methods to set Engine preferences
 */
public interface AdblockEngineSettings
{
  /**
   * Allows edit settings in batch and then save the result
   *
   * This avoids unnecessary blocking the engine and updating callbacks
   * It also allows to configure the engine at once
   *
   * Usage example:
   * <pre>
   * settings.edit()
   *    .addFilter(..)
   *    .addSubscription(..)
   *    .setAcceptableAdsEnabled(true)
   *    .save()
   * </pre>
   *
   * Calling {@link #save()} triggers write operation and applies the changes in runtime which are pending until save().
   */
  interface EditOperation
  {
    /**
     * Enable or disable the AdblockEngine
     * @param enabled true to enable, false otherwise
     * @return EditOperation to allow chaining
     */
    @NotNull
    EditOperation setEnabled(boolean enabled);

    /**
     * Enable or disable acceptable ads
     * Internally it adds or removes the exceptions filter list
     *
     * @param enabled true to enable acceptable ads, false otherwise
     * @return EditOperation to allow chaining
     */
    @NotNull
    EditOperation setAcceptableAdsEnabled(boolean enabled);

    /**
     * Stores the value indicating what connection type is allowed for downloading
     * subscriptions or `null` value to reset and accept all connection types.
     *
     * @param connectionType accepted connection type or `null` value to reset and accept all
     *                       connection types.
     * @return EditOperation to allow chaining
     */
    @NotNull
    EditOperation setAllowedConnectionType(@Nullable ConnectionType connectionType);

    /**
     * Add a single {@link Subscription} to the engine
     *
     * @param subscription new subscription
     * @return EditOperation to allow chaining
     */
    @NotNull
    EditOperation addSubscription(@NotNull Subscription subscription);

    /**
     * Remove a single {@link Subscription} from the engine
     *
     * @param subscription to be removed
     * @return EditOperation to allow chaining
     */
    @NotNull
    EditOperation removeSubscription(@NotNull Subscription subscription);

    /**
     * Add all the {@link Subscription}s to the engine
     *
     * @param subscriptions an iterable collection of Subscriptions
     * @return EditOperation to allow chaining
     */
    @NotNull
    EditOperation addAllSubscriptions(@NotNull Iterable<Subscription> subscriptions);

    /**
     * Remove all subscriptions
     *
     * This DOES NOT restore default subscription list
     *
     * @return EditOperation to allow chaining
     */
    @NotNull
    EditOperation clearSubscriptions();

    /**
     * Adds {@link Filter} to the list of custom filters
     *
     * If the same filter already added, no operation will be performed
     *
     * @param filter {@link Filter} object
     * @see <a href="https://help.eyeo.com/adblockplus/how-to-write-filters">How to write filters</a>
     * @return EditOperation to allow chaining
     */
    @NotNull
    EditOperation addCustomFilter(@NotNull Filter filter);

    /**
     * Removes {@link Filter} from the list of custom filters
     *
     * @param filter {@link Filter} object to be removed, if filter does not exist, no operation will be performed
     * @return EditOperation to allow chaining
     */
    @NotNull
    EditOperation removeCustomFilter(@NotNull Filter filter);

    /**
     * Removes all custom filters
     *
     * @return EditOperation to allow chaining
     */
    @NotNull
    EditOperation clearCustomFilters();

    /**
     * Saves all modifications of {@link AdblockEngineSettings} object.
     *
     * @note The order of chained methods does not matter. Internally save() first performs clear/remove operations
     * and then add operations.
     *
     * This should be explicitly called, otherwise any modifications will be lost
     * Must be the last element in modification chain, eg
     * <pre>
     * settings.edit()
     *    .addFilter(...)
     *    .addSubscription(...)
     *    .setAcceptableAdsEnabled(true)
     *    .save()
     * </pre>
     */
    void save();
  }

  /**
   * @return EditOperation to modify settings.
   */
  @NotNull
  EditOperation edit();

  /**
   * @return true if the AdblockEngine is enabled, false otherwise
   */
  boolean isEnabled();

  /**
   * Indicates if acceptable ads is enabled
   * Internally it adds or removes the exceptions filter list
   *
   * @return true if acceptable ads are enabled, false otherwise
   */
  boolean isAcceptableAdsEnabled();

  /**
   * Retrieves previously stored allowed connection type.
   * @return Preference value, or `null` if it doesn't exist.
   */
  @Nullable
  ConnectionType getAllowedConnectionType();

  /**
   * Return the list of hardcoded eyeo recommended subscriptions from the engine
   *
   * @return a list of {@link Subscription} or empty list if there are no default subscriptions
   */
  @NotNull
  List<Subscription> getDefaultSubscriptions();

  /**
   * Retrieves all subscriptions.
   *
   * @return a list of {@link Subscription} or empty list if there are no listed subscriptions
   */
  @NotNull
  List<Subscription> getListedSubscriptions();

  /**
   * Checks if {@link Subscription} is listed
   *
   * @param subscription {@link Subscription} to be checked
   * @return true if {@link Subscription} is listed
   */
  boolean isListed(@NotNull Subscription subscription);

  /**
   * Retrieves the list of custom filters.
   *
   * @return List of custom {@link Filter}s or empty list if there are no custom filters
   */
  @NotNull
  List<Filter> getListedFilters();

  /**
   * Checks if {@link Filter} is listed. If there are many custom filters added this call may be memory expensive.
   *
   * @param filter {@link Filter} to be checked
   * @return true if {@link Filter} is listed
   */
  boolean isListed(@NotNull Filter filter);

  /**
   * Allows to be notified about enable settings changes.
   */
  interface EnableStateChangedListener
  {
    /**
     * Notifies about {@link AdblockEngine} enable state change.
     *
     * @param isEnabled with current state
     */
    void onAdblockEngineEnableStateChanged(boolean isEnabled);

    /**
     * Notifies about Acceptable Ads enable state change.
     *
     * @param isEnabled with current state
     */
    void onAcceptableAdsEnableStateChanged(boolean isEnabled);
  }

  /**
   * Adds {@link EnableStateChangedListener}
   *
   * @param listener {@link EnableStateChangedListener} object notified about the changes.
   * @return AdblockEngineSettings to allow chaining.
   */
  @NotNull
  AdblockEngineSettings addEnableStateChangedListener(@NotNull EnableStateChangedListener listener);

  /**
   * Removes {@link EnableStateChangedListener}
   *
   * @param listener {@link EnableStateChangedListener} object notified about the changes.
   * @return AdblockEngineSettings to allow chaining.
   */
  @NotNull
  AdblockEngineSettings removeEnableStateChangedListener(@NotNull EnableStateChangedListener listener);

  /**
   * Allows to be notified about changed settings for a {@link Filter}.
   */
  interface FiltersChangedListener
  {
    /**
     * Events informing about {@link Filter} changes.
     */
    enum FilterEvent
    {
      FILTER_ADDED,
      FILTER_REMOVED
    }

    /**
     * Notifies about {@link Filter} change.
     *
     * @param filterToEventMap map of {@link Filter} => {@link FilterEvent}
     */
    void onFilterEvent(@NotNull Map<Filter, FilterEvent> filterToEventMap);
  }

  /**
   * Adds {@link FiltersChangedListener}
   *
   * @param listener {@link FiltersChangedListener} object notified about the changes.
   * @return AdblockEngineSettings to allow chaining.
   */
  @NotNull
  AdblockEngineSettings addFiltersChangedListener(@NotNull FiltersChangedListener listener);

  /**
   * Removes {@link FiltersChangedListener}
   *
   * @param listener {@link FiltersChangedListener} object notified about the changes.
   * @return AdblockEngineSettings to allow chaining.
   */
  @NotNull
  AdblockEngineSettings removeFiltersChangedListener(@NotNull FiltersChangedListener listener);

  /**
   * Allows to be notified about changed settings for a {@link Subscription}.
   */
  interface SubscriptionsChangedListener
  {
    /**
     * Events informing about {@link Subscription} changes.
     */
    enum SubscriptionEvent
    {
      SUBSCRIPTION_ADDED,
      SUBSCRIPTION_REMOVED
    }

    /**
     * Notifies about {@link Subscription} change.
     *
     * @param subscriptionToEventMap map of {@link Subscription} => {@link SubscriptionEvent}
     */
    void onSubscriptionEvent(@NotNull Map<Subscription, SubscriptionEvent> subscriptionToEventMap);
  }

  /**
   * Adds {@link SubscriptionsChangedListener}
   *
   * @param listener {@link SubscriptionsChangedListener} object notified about the changes.
   * @return AdblockEngineSettings to allow chaining.
   */
  @NotNull
  AdblockEngineSettings addSubscriptionsChangedListener(@NotNull SubscriptionsChangedListener listener);

  /**
   * Removes {@link SubscriptionsChangedListener}
   *
   * @param listener {@link SubscriptionsChangedListener} object notified about the changes.
   * @return AdblockEngineSettings to allow chaining.
   */
  @NotNull
  AdblockEngineSettings removeSubscriptionsChangedListener(@NotNull SubscriptionsChangedListener listener);
}
