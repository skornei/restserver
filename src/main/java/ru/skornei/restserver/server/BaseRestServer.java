package ru.skornei.restserver.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import ru.skornei.restserver.annotations.ExceptionHandler;
import ru.skornei.restserver.annotations.RestController;
import ru.skornei.restserver.annotations.RestServer;
import ru.skornei.restserver.annotations.methods.DELETE;
import ru.skornei.restserver.annotations.methods.GET;
import ru.skornei.restserver.annotations.methods.POST;
import ru.skornei.restserver.annotations.methods.PUT;
import ru.skornei.restserver.server.converter.BaseConverter;
import ru.skornei.restserver.server.dictionary.HeaderType;
import ru.skornei.restserver.server.dictionary.ResponseStatus;
import ru.skornei.restserver.server.dictionary.ResponseType;
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
     * Создаем rest сервер
     */
    public BaseRestServer() {
        RestServer restServer = getClass().getAnnotation(RestServer.class);
        if (restServer != null) {
            //Создаем конвертер
            if (BaseConverter.class.isAssignableFrom(restServer.converter())) {
                try {
                    this.converter = (BaseConverter) restServer.converter().newInstance();
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
            throw new NoAnnotationException(getClass().getSimpleName());
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
     * Http сервер
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
            //Информация о запросе
            RequestInfo requestInfo = new RequestInfo(session.getRemoteIpAddress(),
                    session.getHeaders(),
                    session.getParameters());

            //Информация о ответе
            ResponseInfo responseInfo = new ResponseInfo();

            //Получаем контроллер
            Class cls = getController(session.getUri());

            //Создаем контроллер
            Object controller = null;
            try {
                controller = cls.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                //Читаем боди
                if (session.getHeaders().containsKey(HeaderType.CONTENT_LENGTH)) {
                    Integer contentLength = Integer.valueOf(session.getHeaders().get(HeaderType.CONTENT_LENGTH));
                    if (contentLength > 0) {
                        byte[] buffer = new byte[contentLength];
                        session.getInputStream().read(buffer, 0, contentLength);
                        requestInfo.setBody(buffer);
                    }
                }

                //Получаем метод
                ReflectionUtils.MethodInfo methodInfo = null;
                if (session.getMethod() == Method.GET)
                    methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, cls, GET.class);
                else if (session.getMethod() == Method.POST)
                    methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, cls, POST.class);
                else if (session.getMethod() == Method.PUT)
                    methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, cls, PUT.class);
                else if (session.getMethod() == Method.DELETE)
                    methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, cls, DELETE.class);

                //Если метод нашли
                if (methodInfo != null) {
                    //Получаем тип ответа
                    String produces = methodInfo.getProduces();
                    if (produces != null)
                        responseInfo.setType(produces);

                    //Если ждем объект
                    Object paramObject = null;
                    if (converter != null) {
                        Class paramClass = methodInfo.getParamClass();
                        if (paramClass != null && requestInfo.isBodyAvailable()) {
                            paramObject = converter.writeValue(requestInfo.getBody(), paramClass);
                        }
                    }

                    //Если мы ничего не возвращаем
                    if (methodInfo.isVoidResult()) {
                        methodInfo.invoke(requestInfo, responseInfo, paramObject);
                    } else {
                        //Отдаем ответ
                        Object result = methodInfo.invoke(requestInfo, responseInfo, paramObject);
                        if (converter != null)
                            responseInfo.setBody(converter.writeValueAsBytes(result));
                    }

                    //Отправляем ответ
                    return newFixedLengthResponse(responseInfo.getStatus(),
                            responseInfo.getType(),
                            responseInfo.getBodyInputStream(),
                            responseInfo.getBodyLength());
                }
            } catch (Throwable throwable) {
                //Возвращаем ошибку 500 на случай если не настроено иное в ExceptionHandler
                responseInfo.setStatus(ResponseStatus.INTERNAL_SERVER_ERROR);

                //Получаем метод
                ReflectionUtils.MethodInfo methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, cls, ExceptionHandler.class);
                if (methodInfo != null) {
                    //Получаем тип ответа
                    String produces = methodInfo.getProduces();
                    if (produces != null)
                        responseInfo.setType(produces);

                    try {
                        methodInfo.invoke(throwable, responseInfo);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                //Отправляем ответ
                return newFixedLengthResponse(responseInfo.getStatus(),
                        responseInfo.getType(),
                        responseInfo.getBodyInputStream(),
                        responseInfo.getBodyLength());
            }

            //Отвечаем 404
            return newFixedLengthResponse(ResponseStatus.NOT_FOUND, ResponseType.TEXT_PLAIN, ResponseStatus.NOT_FOUND.getDescription());
        }
    }
}
