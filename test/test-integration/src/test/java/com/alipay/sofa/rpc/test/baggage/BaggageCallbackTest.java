/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.rpc.test.baggage;

import com.alipay.sofa.rpc.common.RpcConstants;
import com.alipay.sofa.rpc.config.ApplicationConfig;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.config.MethodConfig;
import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.config.ServerConfig;
import com.alipay.sofa.rpc.context.RpcInvokeContext;
import com.alipay.sofa.rpc.core.exception.SofaRpcException;
import com.alipay.sofa.rpc.core.invoke.SofaResponseCallback;
import com.alipay.sofa.rpc.core.request.RequestBase;
import org.junit.Assert;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 *
 *
 * @author <a href="mailto:zhanggeng.zg@antfin.com">GengZhang</a>
 */
public class BaggageCallbackTest extends BaggageBaseTest {

    @Override
    public void doTest() throws Exception {
        ServerConfig serverConfig = new ServerConfig().setProtocol(RpcConstants.PROTOCOL_TYPE_BOLT).setPort(12299);

        // C服务的服务端
        CSampleServiceImpl refC = new CSampleServiceImpl();
        ProviderConfig<SampleService> serviceBeanC = new ProviderConfig<SampleService>();
        serviceBeanC.setInterfaceId(SampleService.class.getName());
        serviceBeanC.setApplication(new ApplicationConfig().setAppName("CCC"));
        serviceBeanC.setUniqueId("C3");
        serviceBeanC.setRef(refC);
        serviceBeanC.setServer(serverConfig);
        serviceBeanC.setRegister(false);
        serviceBeanC.export();

        // D服务的服务端
        DSampleServiceImpl refD = new DSampleServiceImpl();
        ProviderConfig<SampleService> serviceBeanD = new ProviderConfig<SampleService>();
        serviceBeanD.setInterfaceId(SampleService.class.getName());
        serviceBeanD.setApplication(new ApplicationConfig().setAppName("DDD"));
        serviceBeanD.setUniqueId("D3");
        serviceBeanD.setRef(refD);
        serviceBeanD.setServer(serverConfig);
        serviceBeanD.setRegister(false);
        serviceBeanD.export();

        // B服务里的C服务客户端
        ConsumerConfig referenceBeanC = new ConsumerConfig();
        referenceBeanC.setApplication(new ApplicationConfig().setAppName("BBB"));
        referenceBeanC.setInterfaceId(SampleService.class.getName());
        referenceBeanC.setUniqueId("C3");
        referenceBeanC.setDirectUrl("localhost:12299");
        referenceBeanC.setTimeout(1000);
        MethodConfig methodConfigC = new MethodConfig()
            .setName("hello")
            .setInvokeType(RpcConstants.INVOKER_TYPE_CALLBACK);
        referenceBeanC.setMethods(Collections.singletonList(methodConfigC));
        SampleService sampleServiceC = (SampleService) referenceBeanC.refer();

        // B服务里的D服务客户端
        ConsumerConfig referenceBeanD = new ConsumerConfig();
        referenceBeanD.setApplication(new ApplicationConfig().setAppName("BBB"));
        referenceBeanD.setInterfaceId(SampleService.class.getName());
        referenceBeanD.setUniqueId("D3");
        referenceBeanD.setDirectUrl("localhost:12299?p=1&v=4.0");
        referenceBeanD.setTimeout(1000);
        MethodConfig methodConfigD = new MethodConfig()
            .setName("hello")
            .setInvokeType(RpcConstants.INVOKER_TYPE_CALLBACK);
        referenceBeanD.setMethods(Collections.singletonList(methodConfigD));
        SampleService sampleServiceD = (SampleService) referenceBeanD.refer();

        // B服务的服务端
        BCallbackSampleServiceImpl refB = new BCallbackSampleServiceImpl(sampleServiceC, sampleServiceD);
        ProviderConfig<SampleService> ServiceBeanB = new ProviderConfig<SampleService>();
        ServiceBeanB.setInterfaceId(SampleService.class.getName());
        ServiceBeanB.setApplication(new ApplicationConfig().setAppName("BBB"));
        ServiceBeanB.setUniqueId("B3");
        ServiceBeanB.setRef(refB);
        ServiceBeanB.setServer(serverConfig);
        ServiceBeanB.setRegister(false);
        ServiceBeanB.export();

        // A 服务
        final String[] str = new String[1];
        final CountDownLatch[] latch = new CountDownLatch[] { new CountDownLatch(1) };
        final RpcInvokeContext[] contexts = new RpcInvokeContext[1];
        ConsumerConfig referenceBeanA = new ConsumerConfig();
        referenceBeanA.setApplication(new ApplicationConfig().setAppName("AAA"));
        referenceBeanA.setUniqueId("B3");
        referenceBeanA.setInterfaceId(SampleService.class.getName());
        referenceBeanA.setDirectUrl("localhost:12299");
        referenceBeanA.setTimeout(3000);
        MethodConfig methodConfigA = new MethodConfig()
            .setName("hello")
            .setInvokeType(RpcConstants.INVOKER_TYPE_CALLBACK);
        methodConfigA.setOnReturn(new SofaResponseCallback() {
            @Override
            public void onAppResponse(Object appResponse, String methodName, RequestBase request) {
                Assert.assertNotSame(RpcInvokeContext.getContext(), contexts[0]);
                str[0] = (String) appResponse;
                latch[0].countDown();
            }

            @Override
            public void onAppException(Throwable t, String methodName, RequestBase request) {
                assertEquals("sampleService", t.getMessage());
                assertEquals("sayException", methodName);
            }

            @Override
            public void onSofaException(SofaRpcException sofaException, String methodName,
                                        RequestBase request) {
                // never go to this
                assertEquals("sampleService", sofaException.getMessage());
                assertEquals("sayException", methodName);
            }
        });
        referenceBeanA.setMethods(Collections.singletonList(methodConfigA));
        SampleService service = (SampleService) referenceBeanA.refer();

        // 开始测试
        RpcInvokeContext context = RpcInvokeContext.getContext();
        contexts[0] = context;
        context.putRequestBaggage("reqBaggageB", "a2bbb");
        context.putRequestBaggage("reqBaggageC", "a2ccc");
        context.putRequestBaggage("reqBaggageD", "a2ddd");

        String ret = service.hello();
        Assert.assertEquals(ret, null);
        latch[0].await(5000, TimeUnit.MILLISECONDS);
        ret = str[0];
        Assert.assertEquals(ret, "hello world chello world d");
        Assert.assertEquals(refB.getReqBaggage(), "a2bbb");
        Assert.assertEquals(refC.getReqBaggage(), "a2ccc");
        Assert.assertEquals(refD.getReqBaggage(), "a2ddd");

        Assert.assertEquals(context.getResponseBaggage("respBaggageB"), "b2aaa");
        Assert.assertEquals(context.getResponseBaggage("respBaggageC"), "c2aaa");
        Assert.assertEquals(context.getResponseBaggage("respBaggageD"), "d2aaa");
        Assert.assertNull(context.getResponseBaggage("respBaggageB_force"));
        Assert.assertNull(context.getResponseBaggage("respBaggageC_force"));
        Assert.assertNull(context.getResponseBaggage("respBaggageD_force"));

        RpcInvokeContext.removeContext();
        context = RpcInvokeContext.getContext();
        contexts[0] = context;
        latch[0] = new CountDownLatch(1);
        str[0] = null;
        ret = null;

        ret = service.hello();
        Assert.assertEquals(ret, null);
        latch[0].await(5000, TimeUnit.MILLISECONDS);
        ret = str[0];
        Assert.assertEquals(ret, "hello world chello world d");
        Assert.assertNull(refB.getReqBaggage());
        Assert.assertNull(refC.getReqBaggage());
        Assert.assertNull(refD.getReqBaggage());
        Assert.assertNull(context.getResponseBaggage("respBaggageB"));
        Assert.assertNull(context.getResponseBaggage("respBaggageC"));
        Assert.assertNull(context.getResponseBaggage("respBaggageD"));

        Assert.assertEquals(context.getResponseBaggage("respBaggageB_force"), "b2aaaff");
        Assert.assertEquals(context.getResponseBaggage("respBaggageC_force"), "c2aaaff");
        Assert.assertEquals(context.getResponseBaggage("respBaggageD_force"), "d2aaaff");
    }
}
