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
  {{DEBUG}} console.log('started hiding elements');
  var selectors = JSON.parse({{BRIDGE}}.getElemhideSelectors());
  {{DEBUG}} console.log('parsed selectors: ' + selectors.length);
  for (var i = 0; i < selectors.length; i++)
  {
    var selector = selectors[i];
    if (selector[0] == '#')
    {
      var element = document.getElementById(selector.substr(1));
      if (element != undefined)
      {
        {{DEBUG}} console.log('elem blocked ' + selector);
        element.style.display = 'none';
      };
    }
    else
    {
      var elements = document.getElementsByClassName(selector.substr(1));
      for (var k=0; k < elements.length; k++)
      {
         {{DEBUG}} console.log('elem hidden ' + selector);
         elements[k].style.display = 'none';
      }
    };
  };
  {{DEBUG}} console.log('finished hiding elements');
};