/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.common.requests;

import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.message.ApiVersionsRequestData;
import org.apache.kafka.common.network.ListenerName;
import org.apache.kafka.common.network.Send;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.types.Struct;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.auth.SecurityProtocol;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;

import static org.apache.kafka.common.protocol.ApiKeys.API_VERSIONS;

public class RequestContext implements AuthorizableRequestContext {
    public final RequestHeader header;
    public final String connectionId;
    public final InetAddress clientAddress;
    public final KafkaPrincipal principal;
    public final ListenerName listenerName;
    public final SecurityProtocol securityProtocol;

    public RequestContext(RequestHeader header,//请求头
                          String connectionId,//request 发送方的tcp连接串表示 由Kafka根据一定规则定义，主要用于表示TCP连接
                          InetAddress clientAddress,//发送方的ip
                          KafkaPrincipal principal,//kafka用户认证，用于认证鉴权
                          ListenerName listenerName,//监听器的名称 可以是预定于监听器（如PLAINTEXT） 也可以自定义
                          SecurityProtocol securityProtocol) {// 安全协议类型，目前支持4种：PLAINTEXT、SSL、SASL_PLAINTEXT、SASL_SSL
        this.header = header;
        this.connectionId = connectionId;
        this.clientAddress = clientAddress;
        this.principal = principal;
        this.listenerName = listenerName;
        this.securityProtocol = securityProtocol;
    }

    /**
     * 从 bytebuffer 中提取对应的Request对象以及它的大小
     *
     *
     * ！！！！！！！ apiVersion 请求的作用。broker 收到一个ApiVersionsRequest的时候，返回broker支持的请求类型列表，
     * 包括请求类型名称、支持的最早版本号和最新版本号。kafka-broker-api-version.sh 就是构建ApiVersionsRequest对象 然后发送给broker
     *
     * 版本兼容的问题  kafka 必须保证版本号比最新支持版本还要高的请求也能被处理。客户端发送请求给broker的时候，不知道broker支持哪个版本，就要用ApiVersionRequest去获取
     *
     *
     *
     * @param buffer
     * @return
     */
    public RequestAndSize parseRequest(ByteBuffer buffer) {
        if (isUnsupportedApiVersionsRequest()) {//从请求头中拿到信息判断是不是不支持的版本 是就不解析直接返回
            // Unsupported ApiVersion requests are treated as v0 requests and are not parsed
            ApiVersionsRequest apiVersionsRequest = new ApiVersionsRequest(new ApiVersionsRequestData(), (short) 0, header.apiVersion());
            return new RequestAndSize(apiVersionsRequest, 0);
        } else {
            ApiKeys apiKey = header.apiKey();//从RequestHeader获取到apikey
            try {
                short apiVersion = header.apiVersion();//版本信息
                //解释request
                Struct struct = apiKey.parseRequest(apiVersion, buffer);
                AbstractRequest body = AbstractRequest.parseRequest(apiKey, apiVersion, struct);
                // 封装解析后的请求对象和大小
                return new RequestAndSize(body, struct.sizeOf());
            } catch (Throwable ex) {
                throw new InvalidRequestException("Error getting request for apiKey: " + apiKey +
                        ", apiVersion: " + header.apiVersion() +
                        ", connectionId: " + connectionId +
                        ", listenerName: " + listenerName +
                        ", principal: " + principal, ex);
            }
        }
    }

    public Send buildResponse(AbstractResponse body) {
        ResponseHeader responseHeader = header.toResponseHeader();
        return body.toSend(connectionId, responseHeader, apiVersion());
    }

    private boolean isUnsupportedApiVersionsRequest() {
        return header.apiKey() == API_VERSIONS && !API_VERSIONS.isVersionSupported(header.apiVersion());
    }

    public short apiVersion() {
        // Use v0 when serializing an unhandled ApiVersion response
        if (isUnsupportedApiVersionsRequest())
            return 0;
        return header.apiVersion();
    }

    @Override
    public String listenerName() {
        return listenerName.value();
    }

    @Override
    public SecurityProtocol securityProtocol() {
        return securityProtocol;
    }

    @Override
    public KafkaPrincipal principal() {
        return principal;
    }

    @Override
    public InetAddress clientAddress() {
        return clientAddress;
    }

    @Override
    public int requestType() {
        return header.apiKey().id;
    }

    @Override
    public int requestVersion() {
        return header.apiVersion();
    }

    @Override
    public String clientId() {
        return header.clientId();
    }

    @Override
    public int correlationId() {
        return header.correlationId();
    }
}
