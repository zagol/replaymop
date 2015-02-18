/* Copyright (c) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Copyright (c) 2014 Runtime Verification Inc. All Rights Reserved. */

package jtsan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Class contains medium test for jtsan. Test it medium if 1) Test isn't easy
 * (for definition
 *
 * @see EasyTests). 2) Test doesn't use java.util.concurrent.
 *
 * @author Konstantin Serebryany
 *
 * Added a main method to facilitate testing and a few more tests.
 *
 * @author YilongL
 */
public class MediumTests {

    //------------------ Positive tests ---------------------

    @RaceTest(expectRace = true,
            description = "Two no locked writes to an ArrayList")
    public void arrayListAccessNoLocks() {
        final List<Integer> sharedArrayList = new ArrayList<>();
        new ThreadRunner(2) {

            @Override
            public void thread1() {
                sharedArrayList.add(1);
            }

            @Override
            public void thread2() {
                sharedArrayList.add(2);
            }
        };
    }


    @ExcludedTest(reason = "HashSet loads before instrumentation starts")
    @RaceTest(expectRace = true,
    description = "Two no locked writes to a HashSet")
    public void hashSetAccessNoLocks() {
        final Set<Integer> sharedHashSet = new HashSet<Integer>();
        new ThreadRunner(2) {

            @Override
            public void thread1() {
                sharedHashSet.add(1);
            }

            @Override
            public void thread2() {
                sharedHashSet.add(2);
            }
        };
    }

    @RaceTest(expectRace = true,
            description = "Two unlocked writes to a TreeMap")
    public void treeMapAccessNoLocks() {
        final Map<Integer, Integer> sharedMap = new TreeMap<Integer, Integer>();
        new ThreadRunner(2) {

            @Override
            public void thread1() {
                sharedMap.put(1, 2);
            }

            @Override
            public void thread2() {
                sharedMap.put(2, 1);
            }
        };
    }

    @RaceTest(expectRace = true,
            description = "Two unlocked writes to an array")
    public void arrayAccessNoLocks() {
        final int[] sharedArray = new int[100];
        new ThreadRunner(2) {

            @Override
            public void thread1() {
                sharedArray[42] = 1;
            }

            @Override
            public void thread2() {
                sharedArray[42] = 2;
            }
        };
    }

    @ExcludedTest(reason = "Tsan finds inexact happens-before arc")
    @RaceTest(expectRace = true,
    description = "Two unlocked writes, critcal sections between them")
    public void lockInBetween() {
        new ThreadRunner(2) {
            @Override
            public void thread1() {
                sharedVar = 1;
                synchronized (this) {
                }
            }

            @Override
            public void thread2() {
                longSleep();
                synchronized (this) {
                }
                sharedVar = 2;
            }
        };
    }

    //------------------ Negative tests ---------------------

    @RaceTest(expectRace = false,
            description = "notify/wait")
    public void notifyWait() {
        new ThreadRunner(2) {
            boolean done;

            @Override
            public void setUp() {
                done = false;
            }

            public synchronized void send() {
                done = true;
                notify();
            }

            public synchronized void receive() {
                while (!done) {
                    try {
                        wait();
                    } catch (Exception e) {
                        throw new RuntimeException("Exception in test notifyWait", e);
                    }
                }
            }

            @Override
            public void thread1() {
                shortSleep();
                sharedVar = 1;
                send();
            }

            @Override
            public void thread2() {
                receive();
                sharedVar++;
            }
        };
    }

    @RaceTest(expectRace = false,
            description = "notify/wait; 4 threads")
    public void notifyWait2() {
        new ThreadRunner(4) {
            int counter;
            Object lock1;
            Object lock2;

            @Override
            public void setUp() {
                counter = 2;
                lock1 = new Object();
                lock2 = new Object();
            }

            public synchronized void send() {
                counter--;
                notifyAll();
            }

            public synchronized void receive() {
                while (counter != 0) {
                    try {
                        wait();
                    } catch (Exception e) {
                        throw new RuntimeException("Exception in test notifyWait2", e);
                    }
                }
            }

            @Override
            public void thread1() {
                shortSleep();
                synchronized (lock1) {
                    sharedVar = 1;
                }
                send();
            }

            @Override
            public void thread2() {
                receive();
                synchronized (lock2) {
                    sharedVar++;
                }
            }

            @Override
            public void thread3() {
                thread1();
            }

            @Override
            public void thread4() {
                thread2();
            }
        };
    }


    @RaceTest(expectRace = false,
            description = "Two writes to different deep object field")
    public void deepField() {
        new ThreadRunner(2) {
            class DeepObject {
                DeepObject next;
                int value1;
                int value2;
            }

            DeepObject o;

            @Override
            public void setUp() {
                o = new DeepObject();
                o.next = new DeepObject();
                o.next.next = new DeepObject();
                o.next.next.next = new DeepObject();
            }

            @Override
            public void thread1() {
                o.next.next.next.value1 = 1;
            }

            @Override
            public void thread2() {
                o.next.next.next.value2 = 2;
            }
        };
    }

    @RaceTest(expectRace = false,
            description = "Passing ownership via a locked map")
    public void passingViaLockedMap() {
        new ThreadRunner(2) {
            private Map<Integer, Object> map;

            @Override
            public void setUp() {
                map = new TreeMap<Integer, Object>();
                sharedObject = 0L;
            }

            @Override
            public void thread1() {
                sharedObject = 42;
                synchronized (this) {
                    map.put(1, sharedObject);
                }
            }

            @Override
            public void thread2() {
                Integer message;
                while (true) {
                    synchronized (this) {
                        message = (Integer) map.get(1);
                        if (message != null) break;
                    }
                    shortSleep();
                }
                message++;
            }
        };
    }

    @RaceTest(expectRace = false,
            description = "Volatile boolean is used as a synchronization")
    public void syncWithLocalVolatile() {
        new ThreadRunner(2) {
            volatile boolean volatileBoolean;

            @Override
            public void setUp() {
                volatileBoolean = false;
            }

            @Override
            public void thread1() {
                sharedVar = 1;
                volatileBoolean = true;
            }

            @Override
            public void thread2() {
                while (!volatileBoolean) ;
                sharedVar = 2;
            }
        };
    }

    @RaceTest(expectRace = false,
            description = "Sending a message via a locked object")
    public void messageViaLockedObject() {
        new ThreadRunner(2) {
            Integer locked_object;

            @Override
            public void thread1() {
                Integer message = 42;
                synchronized (this) {
                    locked_object = message;
                }
            }

            @Override
            public void thread2() {
                Integer message;
                while (true) {
                    synchronized (this) {
                        message = locked_object;
                        if (message != null) break;
                    }
                    shortSleep();
                }
                message++;
            }
        };
    }

    @RaceTest(expectRace = false,
            description = "Passing ownership via a locked boolean")
    public void passingViaLockedBoolean() {
        new ThreadRunner(2) {
            private boolean signal;

            @Override
            public void thread1() {
                sharedVar = 1;
                longSleep();
                synchronized (this) {
                    signal = true;
                }
            }

            @Override
            public void thread2() {
                while (true) {
                    synchronized (this) {
                        if (signal) break;
                    }
                    shortSleep();
                }
                sharedVar = 2;
            }
        };
    }


    @RaceTest(expectRace = false,
            description = "Passing object ownership via a locked boolean; 4 threads")
    public void passingViaLockedBoolean2() {
        new ThreadRunner(4) {
            Object lock;
            int counter;

            @Override
            public void setUp() {
                sharedObject = 0.0f;
                lock = new Object();
                counter = 2;
            }

            @Override
            public void thread1() {
                synchronized (lock) {
                    sharedObject = (Float) sharedObject + 1;
                }
                synchronized (this) {
                    counter--;
                }
            }

            @Override
            public void thread2() {
                thread1();
            }

            @Override
            public void thread3() {
                Integer message;
                while (true) {
                    synchronized (this) {
                        if (counter == 0) break;
                    }
                    shortSleep();
                }
            }

            @Override
            public void thread4() {
                thread3();
            }
        };
    }

    @RaceTest(expectRace = false,
            description = "Two unlocked writes to an array at different offsets")
    public void arrayDifferentOffsets() {
        final int[] sharedArray = new int[100];
        new ThreadRunner(2) {

            @Override
            public void thread1() {
                sharedArray[42] = 1;
            }

            @Override
            public void thread2() {
                sharedArray[43] = 2;
            }
        };
    }

    @RaceTest(expectRace = false,
            description = "Test correctness of monitor exit with Exception")
    public void exceptionWithSync() {
        new ThreadRunner(2) {
            @Override
            public synchronized void thread1() {
                sharedVar++;
                throw new RuntimeException("Exit from synchronized method with this Exception");
            }

            @Override
            public void thread2() {
                shortSleep();
                synchronized(this) {
                    sharedVar++;
                }
            }
        };
    }

    public static void main(String[] args) {
        MediumTests tests = new MediumTests();
        // positive tests
        if (args[0].equals("positive")) {
            tests.arrayListAccessNoLocks();
            tests.hashSetAccessNoLocks();
            tests.treeMapAccessNoLocks();
            tests.arrayAccessNoLocks();
            tests.lockInBetween();
        } else {
            // negative tests
            tests.notifyWait();
            tests.notifyWait2();
            tests.deepField();
            tests.passingViaLockedMap();
            tests.syncWithLocalVolatile();
            tests.messageViaLockedObject();
            tests.passingViaLockedBoolean();
            tests.passingViaLockedBoolean2();
            tests.arrayDifferentOffsets();
            tests.exceptionWithSync();
        }
    }

}
