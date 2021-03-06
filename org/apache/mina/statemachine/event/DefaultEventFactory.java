/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.mina.statemachine.event;

import java.lang.reflect.Method;

import org.apache.mina.statemachine.context.StateContext;

/**
 * Default {@link EventFactory} implementation. Uses the method's name as
 * event id.
 * 
 * @author The Apache MINA Project (dev@mina.apache.org)
 * @version $Rev: 586090 $, $Date: 2007-10-18 21:12:08 +0200 (jeu, 18 oct 2007) $
 */
public class DefaultEventFactory implements EventFactory {

    public Event create(StateContext context, Method method, Object[] arguments) {
        return new Event(method.getName(), context, arguments);
    }

}
