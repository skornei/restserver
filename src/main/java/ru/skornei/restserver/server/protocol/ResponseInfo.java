package ru.skornei.restserver.server.protocol;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;
import ru.skornei.restserver.server.dictionary.ResponseStatus;
import ru.skornei.restserver.server.dictionary.ResponseType;

public class ResponseInfo extends NanoHTTPD.Response {

    /**
     * Ответ по умолчанию
     */
    public ResponseInfo() {
        super(ResponseStatus.OK,
                ResponseType.TEXT_PLAIN,
                null,
                0);
    }

    /**
     * Задать тело ответа
     * @param data строка
     */
    public void setData(String data) {
        if (data != null) {
            InputStream inputStream = new ByteArrayInputStream(data.getBytes());
            setData(inputStream);
        }
    }

    /**
     * Задать тело ответа
     * @param data данные
     */
    public void setData(byte[] data) {
        if (data != null) {
            InputStream inputStream = new ByteArrayInputStream(data);
            setData(inputStream);
        }
    }
}
