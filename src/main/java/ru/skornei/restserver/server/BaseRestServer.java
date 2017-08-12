package ru.skornei.restserver.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import ru.skornei.restserver.Cache;
import ru.skornei.restserver.annotations.ExceptionHandler;
import ru.skornei.restserver.annotations.RestController;
import ru.skornei.restserver.annotations.RestServer;
import ru.skornei.restserver.annotations.methods.DELETE;
import ru.skornei.restserver.annotations.methods.GET;
import ru.skornei.restserver.annotations.methods.POST;
import ru.skornei.restserver.annotations.methods.PUT;
import ru.skornei.restserver.server.authentication.BaseAuthentication;
import ru.skornei.restserver.server.converter.BaseConverter;
import ru.skornei.restserver.server.dictionary.HeaderType;
import ru.skornei.restserver.server.dictionary.ResponseStatus;
import ru.skornei.restserver.server.exceptions.NoAnnotationException;
import ru.skornei.restserver.server.protocol.RequestInfo;
import ru.skornei.restserver.server.protocol.ResponseInfo;
import ru.skornei.restserver.utils.ReflectionUtils;

public abstract class BaseRestServer {

    /**
     * Http сервер
     */
    private HttpServer httpServer;

    /**
     * Контроллеры обработчики запросов
     */
    private Map<String, Class> controllers = new HashMap<>();

    /**
     * Конвертер объектов
     */
    private BaseConverter converter;

    /**
     * Аутентификация
     */
    private BaseAuthentication authentication;

    /**
     * Создаем rest сервер
     */
    public BaseRestServer() {
        RestServer restServer = getClass().getAnnotation(RestServer.class);
        if (restServer != null) {
            //Создаем конвертер
            if (!restServer.converter().equals(void.class) &&
                    BaseConverter.class.isAssignableFrom(restServer.converter())) {
                try {
                    this.converter = (BaseConverter) restServer.converter().newInstance();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }

            //Создаем класс аутентификации
            if (!restServer.authentication().equals(void.class) &&
                    BaseAuthentication.class.isAssignableFrom(restServer.authentication())) {
                try {
                    this.authentication = (BaseAuthentication) restServer.authentication().newInstance();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }

            //Получаем контроллеры
            for (Class<?> cls : restServer.controllers()) {
                if (cls.isAnnotationPresent(RestController.class)) {
                    RestController restController = cls.getAnnotation(RestController.class);
                    controllers.put(restController.value(), cls);
                }
            }

            //Создаем сервер
            httpServer = new HttpServer(restServer.port());
        } else {
            throw new NoAnnotationException(getClass().getSimpleName(), RestServer.class.getSimpleName());
        }
    }

    /**
     * Запускаем сервер
     * @throws IOException
     */
    public void start() throws IOException {
        httpServer.start();
    }

    /**
     * Останавливаем сервер
     */
    public void stop() {
        httpServer.stop();
    }

    /**
     * Получить контроллер для этого адреса
     * @param uri адрес
     * @return контроллер
     */
    private Class getController(String uri) {
        if (controllers.containsKey(uri))
            return controllers.get(uri);

        return null;
    }

    /**
     * Http server
     */
    private class HttpServer extends NanoHTTPD {

        public HttpServer(int port) {
            super(port);
        }

        /**
         * Получаем данные от клиента
         *
         * @param session сессия
         * @return ответ
         */
        @Override
        public Response serve(IHTTPSession session) {
            //Request information
            RequestInfo requestInfo = new RequestInfo(session.getRemoteIpAddress(),
                    session.getHeaders(),
                    session.getParameters());

            //Reply Information
            ResponseInfo responseInfo = new ResponseInfo();

            //Get the controller
            Class cls = getController(session.getUri());

            //Found the controller
            if (cls != null) {
                //Create a controller
                Object controller = null;
                try {
                    controller = cls.newInstance();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                //Created the controller
                if (controller != null) {
                    try {
                        //Read body
                        if (session.getHeaders().containsKey(HeaderType.CONTENT_LENGTH)) {
                            Integer contentLength = Integer.valueOf(session.getHeaders().get(HeaderType.CONTENT_LENGTH));
                            if (contentLength > 0) {
                                byte[] buffer = new byte[contentLength];
                                session.getInputStream().read(buffer, 0, contentLength);
                                requestInfo.setBody(buffer);
                            }
                        }

                        //Get the method
                        ReflectionUtils.MethodInfo methodInfo = null;
                        if (session.getMethod() == Method.GET)
                            methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, cls, GET.class);
                        else if (session.getMethod() == Method.POST)
                            methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, cls, POST.class);
                        else if (session.getMethod() == Method.PUT)
                            methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, cls, PUT.class);
                        else if (session.getMethod() == Method.DELETE)
                            methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, cls, DELETE.class);

                        //If the method is found
                        if (methodInfo != null) {
                            //RequiresAuthentication
                            if (authentication != null &&
                                    methodInfo.isRequiresAuthentication() &&
                                    !authentication.authentication(requestInfo)) {
                                //Answer 401
                                return newFixedLengthResponse(ResponseStatus.UNAUTHORIZED,
                                        NanoHTTPD.MIME_PLAINTEXT,
                                        ResponseStatus.UNAUTHORIZED.getDescription());
                            }

                            //Accept
                            String accept = methodInfo.getAccept();
                            if (accept != null) {
                                if (session.getHeaders().containsKey(HeaderType.CONTENT_TYPE)) {
                                    String contentType = session.getHeaders().get(HeaderType.CONTENT_TYPE);
                                    if (!accept.equals(contentType)) {
                                        //Answer 415
                                        return newFixedLengthResponse(ResponseStatus.UNSUPPORTED_MEDIA_TYPE,
                                                NanoHTTPD.MIME_PLAINTEXT,
                                                ResponseStatus.UNSUPPORTED_MEDIA_TYPE.getDescription());
                                    }
                                }
                            }

                            //Get the response type
                            String produces = methodInfo.getProduces();
                            if (produces != null)
                                responseInfo.setType(produces);

                            //If we are waiting for an object
                            Object paramObject = null;
                            if (converter != null) {
                                Class paramClass = methodInfo.getParamClass();
                                if (paramClass != null && requestInfo.isBodyAvailable()) {
                                    paramObject = converter.writeValue(requestInfo.getBody(), paramClass);
                                }
                            }

                            //If we do not return anything
                            if (methodInfo.isVoidResult()) {
                                methodInfo.invoke(Cache.getContext(),
                                        requestInfo,
                                        responseInfo,
                                        paramObject);
                            } else {
                                //Return the answer
                                Object result = methodInfo.invoke(Cache.getContext(),
                                        requestInfo,
                                        responseInfo,
                                        paramObject);

                                if (converter != null)
                                    responseInfo.setBody(converter.writeValueAsBytes(result));
                            }

                            //Sending response
                            return newFixedLengthResponse(responseInfo.getStatus(),
                                    responseInfo.getType(),
                                    responseInfo.getBodyInputStream(),
                                    responseInfo.getBodyLength());
                        }
                    } catch (Throwable throwable) {
                        //Return error 500 in case it is not otherwise configured in ExceptionHandler
                        responseInfo.setStatus(ResponseStatus.INTERNAL_SERVER_ERROR);

                        //Get the method
                        ReflectionUtils.MethodInfo methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, cls, ExceptionHandler.class);
                        if (methodInfo != null) {
                            //Get the response type
                            String produces = methodInfo.getProduces();
                            if (produces != null)
                                responseInfo.setType(produces);

                            try {
                                methodInfo.invoke(throwable, responseInfo);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        //Sending response
                        return newFixedLengthResponse(responseInfo.getStatus(),
                                responseInfo.getType(),
                                responseInfo.getBodyInputStream(),
                                responseInfo.getBodyLength());
                    }
                }
            }

            //Answer 404
            return newFixedLengthResponse(ResponseStatus.NOT_FOUND,
                    NanoHTTPD.MIME_PLAINTEXT,
                    ResponseStatus.NOT_FOUND.getDescription());
        }
    }
}
