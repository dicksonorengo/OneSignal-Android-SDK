/**
 * Modified MIT License
 * <p>
 * Copyright 2018 OneSignal
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.test.onesignal;

import com.onesignal.BuildConfig;
import com.onesignal.MockOutcomeEventsController;
import com.onesignal.MockOutcomeEventsRepository;
import com.onesignal.MockOutcomeEventsService;
import com.onesignal.OSSessionManager;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalDbHelper;
import com.onesignal.OutcomeEvent;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.List;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

@Config(packageName = "com.onesignal.example",
        constants = BuildConfig.class,
        instrumentedPackages = {"com.onesignal"},
        sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class OutcomeEventTests {

    private static final String OUTCOME_NAME = "testing";

    private MockOutcomeEventsController controller;
    private MockOutcomeEventsRepository repository;
    private MockOutcomeEventsService service;
    private OneSignalDbHelper dbHelper;

    private static List<OutcomeEvent> outcomeEvents;

    public interface OutcomeEventsHandler {

        void setOutcomes(List<OutcomeEvent> outcomes);
    }

    private OutcomeEventsHandler handler = new OutcomeEventsHandler() {
        @Override
        public void setOutcomes(List<OutcomeEvent> outcomes) {
            outcomeEvents = outcomes;
        }
    };

    @BeforeClass // Runs only once, before any tests
    public static void setUpClass() {
        ShadowLog.stream = System.out;
        TestHelpers.beforeTestSuite();
    }

    @Before // Before each test
    public void beforeEachTest() {
        outcomeEvents = null;

        OneSignal.OutcomeSettings outcomeSettings = OneSignal.OutcomeSettings.Builder.newInstance()
                .setCacheActive(true)
                .build();
        OSSessionManager sessionManager = new OSSessionManager();
        dbHelper = OneSignalDbHelper.getInstance(RuntimeEnvironment.application);
        service = new MockOutcomeEventsService();
        repository = new MockOutcomeEventsRepository(service, dbHelper);
        controller = new MockOutcomeEventsController(sessionManager, repository);
        controller.setOutcomeSettings(outcomeSettings);
    }

    @After
    public void tearDown() throws Exception {
        dbHelper.cleanOutcomeDatabase();
        dbHelper.close();
    }

    @Test
    public void testOutcomeSuccess() throws Exception {
        service.setSuccess(true);

        controller.sendOutcomeEvent(OUTCOME_NAME, (OneSignal.OutcomeCallback) null);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_SUCCESS").start();

        threadAndTaskWait();
        Assert.assertEquals(0, outcomeEvents.size());
        Assert.assertEquals("{\"id\":\"testing\",\"device_type\":2}", service.getLastJsonObjectSent());
    }

    @Test
    public void testOutcomeWithValueSuccess() throws Exception {
        service.setSuccess(true);

        controller.sendOutcomeEvent(OUTCOME_NAME, 1.1f, null);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_SUCCESS").start();

        threadAndTaskWait();
        Assert.assertEquals(0, outcomeEvents.size());
        Assert.assertEquals("{\"id\":\"testing\",\"device_type\":2,\"weight\":1.1}", service.getLastJsonObjectSent());
    }

    @Test
    public void testOutcomeFailWithoutCache() throws Exception {
        service.setSuccess(false);
        controller.setOutcomeSettings(OneSignal.OutcomeSettings.Builder.newInstance()
                .setCacheActive(false)
                .build());

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAIL").start();

        threadAndTaskWait();
        assertTrue(outcomeEvents.size() == 0);
        Assert.assertEquals("{\"id\":\"testing\",\"device_type\":2}", service.getLastJsonObjectSent());
    }

    @Test
    public void testUniqueOutcomeFailSavedOnDB() throws Exception {
        service.setSuccess(false);

        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);

        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAIL").start();

        threadAndTaskWait();
        assertTrue(outcomeEvents.size() == 1);
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getName());
        Assert.assertEquals("{\"id\":\"testing\",\"device_type\":2}", service.getLastJsonObjectSent());

        controller.clearOutcomes();

        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAIL").start();

        threadAndTaskWait();
        assertTrue(outcomeEvents.size() == 2);
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getName());
        assertEquals(OUTCOME_NAME, outcomeEvents.get(1).getName());
    }

    @Test
    public void testUniqueOutcomeFailSavedOnDBResetSession() throws Exception {
        service.setSuccess(false);

        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);

        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAIL").start();

        threadAndTaskWait();
        assertTrue(outcomeEvents.size() == 1);
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getName());
    }

    @Test
    public void testOutcomeFailSavedOnDB() throws Exception {
        service.setSuccess(false);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAIL").start();

        threadAndTaskWait();
        assertTrue(outcomeEvents.size() > 0);
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getName());
    }

    @Test
    public void testOutcomeMultipleFailsSavedOnDB() throws Exception {
        service.setSuccess(false);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        controller.sendOutcomeEvent(OUTCOME_NAME + "1");
        controller.sendOutcomeEvent(OUTCOME_NAME + "2");
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAIL").start();
        threadAndTaskWait();

        Assert.assertEquals(3, outcomeEvents.size());

        controller.sendOutcomeEvent(OUTCOME_NAME + "3");
        controller.sendOutcomeEvent(OUTCOME_NAME + "4");
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAILS").start();

        threadAndTaskWait();
        Assert.assertEquals(5, outcomeEvents.size());
    }

    @Test
    public void testSendFailedOutcomesOnDB() throws Exception {
        service.setSuccess(false);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        controller.sendOutcomeEvent(OUTCOME_NAME + "1");
        controller.sendOutcomeEvent(OUTCOME_NAME + "2", 1);
        controller.sendOutcomeEvent(OUTCOME_NAME + "3");
        controller.sendOutcomeEvent(OUTCOME_NAME + "4", 1.1f);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAILS").start();

        threadAndTaskWait();
        Assert.assertEquals(5, outcomeEvents.size());

        service.setSuccess(true);

        controller.sendSavedOutcomes();
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAILS").start();
        threadAndTaskWait();

        Assert.assertEquals(0, outcomeEvents.size());
    }

    @Test
    public void testSendFailedOutcomeWithValueOnDB() throws Exception {
        service.setSuccess(false);

        controller.sendOutcomeEvent(OUTCOME_NAME, 1.1f);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAILS").start();

        threadAndTaskWait();
        Assert.assertEquals(1, outcomeEvents.size());
        Assert.assertEquals("{\"weight\":1.1}", outcomeEvents.get(0).getParams());
        Assert.assertEquals("{\"id\":\"testing\",\"device_type\":2,\"weight\":1.1}", service.getLastJsonObjectSent());

        service.setSuccess(true);
        service.resetLastJsonObjectSent();
        controller.sendSavedOutcomes();
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAILS").start();
        threadAndTaskWait();

        Assert.assertEquals(0, outcomeEvents.size());
        Assert.assertEquals("{\"id\":\"testing\",\"timestamp\":0,\"weight\":1.1,\"device_type\":2}", service.getLastJsonObjectSent());
    }

    private static void threadAndTaskWait() throws Exception {
        TestHelpers.threadAndTaskWait();
    }
}