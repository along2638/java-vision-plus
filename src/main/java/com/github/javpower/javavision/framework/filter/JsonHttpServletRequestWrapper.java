package com.github.javpower.javavision.framework.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StreamUtils;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 把请求体缓存成字节数组，允许后续重复读取。
 *
 * @author gc.x
 * @date 2021/12/19
 **/
@Slf4j
public class JsonHttpServletRequestWrapper extends HttpServletRequestWrapper {
    private byte[] body = new byte[0];

    public JsonHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        //保存一份InputStream，将其转换为字节数组
        try {
            body = StreamUtils.copyToByteArray(request.getInputStream());
        } catch (IOException e) {
            // 读不出来就当作空请求体，后续由业务或框架给出参数错误，不要让 NPE 冒到上层
            log.warn("读取请求体失败，按空请求体处理", e);
        }
    }

    /**
     * 转换成String
     *
     * @return
     */
    public String getBodyString() {
        String bodyString = new String(body, StandardCharsets.UTF_8);
        return bodyString;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    /**
     * 把保存好的InputStream，传下去
     *
     * @return
     * @throws IOException
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return bais.read();
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setReadListener(ReadListener listener) {

            }
        };
    }
}
