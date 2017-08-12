package ru.skornei.restserver.server.protocol;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import ru.skornei.restserver.server.dictionary.ContentType;
import ru.skornei.restserver.server.dictionary.ResponseStatus;

public class ResponseInfo {

    /**
     * Статус
     */
    private ResponseStatus status = ResponseStatus.OK;

    /**
     * Тип
     */
    private String type = ContentType.TEXT_PLAIN;

    /**
     * Тело запроса
     */
    private byte[] body;

    public ResponseStatus getStatus() {
        return status;
    }

    public void setStatus(ResponseStatus status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public InputStream getBodyInputStream() {
        if (body != null)
            return new ByteArrayInputStream(body);

        return null;
    }

    public int getBodyLength() {
        if (body != null)
            return body.length;

        return 0;
    }
}
