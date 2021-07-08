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
package org.apache.rocketmq.remoting.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import java.nio.ByteBuffer;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.common.RemotingUtil;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

/**
 NettyDecoder所有的操作都是基于RemotingCommand的decode来实现
 继承LengthFieldBasedFrameDecoder，netty的编码要求，该实现是基于长度的要求解决拆包粘包问题
 将ByteBuf的数据输入按照长度的要求，数据解码到ByteBuf的存储体内
 将netty封装的ByteBuf对象转换为java的Nio的ByteBuffer对象：ByteBuf.nioBuffer()

 解码的操作过程基于编码的规范来
 1，获得数据的最大有效位置
 2，获得第一个int位的值，该值为数据长度，包括头数据和报文数据
 3，byteBuffer操作获得头部的数据
 4，将数据的最大有效位置减去 4（标识），再减去头数据的长度，得到报文的长度
 5，根据报文长度，byteBuffer操作获得报文长度
 */
public class NettyDecoder extends LengthFieldBasedFrameDecoder {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(RemotingHelper.ROCKETMQ_REMOTING);

    //基于长度的解码器，定义长度的标准，友好处理粘包及拆包
    private static final int FRAME_MAX_LENGTH =
        Integer.parseInt(System.getProperty("com.rocketmq.remoting.frameMaxLength", "16777216"));

    public NettyDecoder() {
        super(FRAME_MAX_LENGTH, 0, 4, 0, 4);
    }

    @Override
    public Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = null;
        try {
            //基于父类的解码，共性操作,解决拆包粘包问题
            frame = (ByteBuf) super.decode(ctx, in);
            if (null == frame) {
                return null;
            }
            //将netty的bytebuf转换为java标准的butebuffer
            ByteBuffer byteBuffer = frame.nioBuffer();
            //按照编码协议，将结果转换为对象RemotingCommand，封装ByteBuf的字节到RemotingCommand中去
            return RemotingCommand.decode(byteBuffer);
        } catch (Exception e) {
            log.error("decode exception, " + RemotingHelper.parseChannelRemoteAddr(ctx.channel()), e);
            RemotingUtil.closeChannel(ctx.channel());
        } finally {
            if (null != frame) {
                frame.release();
            }
        }

        return null;
    }
}
