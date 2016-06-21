/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-2016 Eyeo GmbH
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

package org.adblockplus.libadblockplus.tests;

import org.adblockplus.libadblockplus.MockUpdateAvailableCallback;
import org.adblockplus.libadblockplus.NoOpUpdaterCallback;
import org.adblockplus.libadblockplus.ServerResponse;

import org.junit.Test;

public class FilterEngineUpdaterTest extends UpdaterTest
{
    @Test
    public void testSetRemoveUpdateAvailableCallback() throws InterruptedException
    {
        mockWebRequest.response.setStatus(ServerResponse.NsStatus.OK);
        mockWebRequest.response.setResponseStatus(200);
        mockWebRequest.response.setResponse(
            "{\n" +
            "   \"test\": {\n" +
            "       \"version\": \"1.0.2\",\n" +
            "       \"url\": \"https://downloads.adblockplus.org/test-1.0.2.tar.gz?update\"\n" +
            "   }\n" +
            "}");

        MockUpdateAvailableCallback mockUpdateAvailableCallback =
            new MockUpdateAvailableCallback(0);
        filterEngine.setUpdateAvailableCallback(mockUpdateAvailableCallback);
        filterEngine.forceUpdateCheck(new NoOpUpdaterCallback());
        Thread.sleep(1000);
        assertEquals(1, mockUpdateAvailableCallback.getTimesCalled());

        filterEngine.removeUpdateAvailableCallback();
        filterEngine.forceUpdateCheck(new NoOpUpdaterCallback());
        Thread.sleep(1000);
        assertEquals(1, mockUpdateAvailableCallback.getTimesCalled());
    }
}
