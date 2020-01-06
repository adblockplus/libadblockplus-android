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

{
   {{DEBUG}} console.log('starting injecting css rules');
   var selectors = JSON.parse({{BRIDGE}}.getElemhideSelectors());
   {{DEBUG}} console.log('parsed selectors: ' + selectors.length);
   var head = document.getElementsByTagName("head")[0];
   var style = document.createElement("style");
   head.appendChild(style);
   var sheet = style.sheet ? style.sheet : style.styleSheet;
   for (var i=0; i<selectors.length; i++)
   {
     if (sheet.insertRule)
     {
       sheet.insertRule(selectors[i] + ' { display: none !important; }', 0);
     }
     else
     {
       sheet.addRule(selectors[i], 'display: none !important;', 0);
     }
   }
   {{DEBUG}} console.log('finished injecting css rules');
}