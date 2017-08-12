package ru.skornei.restserver.server.authentication;

import ru.skornei.restserver.server.protocol.RequestInfo;

public interface BaseAuthentication {

    /**
     * Аутентификация
     * @param requestInfo параметры запроса
     * @return прошла аутентификация или нет
     */
    boolean authentication(RequestInfo requestInfo);
}
