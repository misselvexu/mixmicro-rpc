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
package com.alipay.sofa.rpc.bootstrap.triple;

import com.alipay.sofa.rpc.bootstrap.DefaultProviderBootstrap;
import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.ext.Extension;
import com.alipay.sofa.rpc.log.Logger;
import com.alipay.sofa.rpc.log.LoggerFactory;
import com.alipay.sofa.rpc.proxy.ProxyFactory;
import com.alipay.sofa.rpc.server.ProviderProxyInvoker;

import java.lang.reflect.Method;

/**
 * Provider bootstrap for grpc
 *
 * @author <a href=mailto:yqluan@gmail.com>Yanqiang Oliver Luan (neokidd)</a>
 */
@Extension("tri")
public class TripleProviderBootstrap<T> extends DefaultProviderBootstrap<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TripleProviderBootstrap.class);

    /**
     * 构造函数
     *
     * @param providerConfig 服务发布者配置
     */
    protected TripleProviderBootstrap(ProviderConfig<T> providerConfig) {
        super(providerConfig);
    }

    @Override
    protected void preProcessProviderTarget(ProviderConfig providerConfig, ProviderProxyInvoker providerProxyInvoker) {
        Class<?> implClass = providerConfig.getRef().getClass();
        try {
            Method method = implClass.getMethod("setProxiedImpl", providerConfig.getProxyClass());
            Object obj = ProxyFactory.buildProxy(providerConfig.getProxy(), providerConfig.getProxyClass(),
                providerProxyInvoker);
            method.invoke(providerConfig.getRef(), obj);
        } catch (NoSuchMethodException e) {
            LOGGER
                .info(
                    "{} don't hava method setProxiedImpl, will treated as origin provider service instead of grpc service.",
                    implClass);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to set sofa proxied service impl to stub, please make sure your stub "
                    + "was generated by the sofa-protoc-compiler.", e);
        }
    }

    @Override
    public void export() {
        Class enclosingClass = this.getProviderConfig().getProxyClass().getEnclosingClass();
        Method sofaStub = null;
        String serviceName = this.getProviderConfig().getInterfaceId();
        try {
            sofaStub = enclosingClass.getDeclaredMethod("getServiceName");
            serviceName = (String) sofaStub.invoke(null);
        } catch (Throwable e) {
            //ignore
        }
        this.getProviderConfig().setVirtualInterfaceId(serviceName);
        super.export();
    }
}
