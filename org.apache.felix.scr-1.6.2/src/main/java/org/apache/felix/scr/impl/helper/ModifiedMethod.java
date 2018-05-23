/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.scr.impl.helper;


import org.apache.felix.scr.impl.manager.AbstractComponentManager;


public class ModifiedMethod extends ActivateMethod
{

    public ModifiedMethod( final SimpleLogger logger, final String methodName,
        final Class componentClass, final boolean isDS11, final boolean isDS12Felix )
    {
        super( logger, methodName, methodName != null, componentClass, isDS11, isDS12Felix );
    }


    protected boolean acceptEmpty()
    {
        return true;
    }


    protected String getMethodNamePrefix()
    {
        return "modified";
    }
}