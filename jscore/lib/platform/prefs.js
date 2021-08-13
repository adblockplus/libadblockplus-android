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

/**
 * This is an interim stub, will be replaced with implementation
 * 
 * For now it only remembers its state in memory
 */
export let Prefs = {
  enabled: true,
  first_run: false, //FIXME: set to true when we have a synchronizer
  on() { this.enabled = true },
  off() { this.enabled = false },
  notificationdata: {},
  blocked_total: 0,
  show_statsinpopup: true,
  notificationurl: "https://notification.adblockplus.org/notification.json",
  notifications_ignoredcategories: [],
  subscriptions_exceptionsurl: "https://easylist-downloads.adblockplus.org/exceptionrules.txt",
};
