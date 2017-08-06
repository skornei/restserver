package ru.skornei.restserver.server;

import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import ru.skornei.restserver.annotations.ExceptionHandler;
import ru.skornei.restserver.annotations.RestController;
import ru.skornei.restserver.annotations.methods.DELETE;
import ru.skornei.restserver.annotations.methods.GET;
import ru.skornei.restserver.annotations.methods.POST;
import ru.skornei.restserver.annotations.methods.PUT;
import ru.skornei.restserver.server.converter.BaseConverter;
import ru.skornei.restserver.server.dictionary.ResponseStatus;
import ru.skornei.restserver.server.dictionary.ResponseType;
import ru.skornei.restserver.server.protocol.RequestInfo;
import ru.skornei.restserver.server.protocol.ResponseInfo;
import ru.skornei.restserver.utils.ReflectionUtils;

public final class RestServer extends NanoHTTPD {

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
     * @param port порт
     */
    public RestServer(int port) {
        super(port);
    }

    /**
     * Добавляем контроллер
     * @param cls класс контроллер
     */
    public void addController(Class cls) {
        if (cls.isAnnotationPresent(RestController.class)) {
            RestController restController = (RestController) cls.getAnnotation(RestController.class);
            controllers.put(restController.value(), cls);
        }
    }

    /**
     * Устанавливаем конвертер
     * @param converter конвертер
     */
    public void setConverter(BaseConverter converter) {
        this.converter = converter;
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
     * Получаем данные от клиента
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
            String contentLengthStr = session.getHeaders().get("content-length");
            if (contentLengthStr != null) {
                Integer contentLength = Integer.valueOf(contentLengthStr);
                if (contentLength > 0) {
                    byte[] buffer = new byte[contentLength];
                    session.getInputStream().read(buffer, 0, contentLength);
                    requestInfo.setBody(buffer);
                }
            }

            //Получаем метод
            ReflectionUtils.MethodInfo methodInfo = null;
            if (session.getMethod() == Method.GET)
                methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, GET.class);
            else if (session.getMethod() == Method.POST)
                methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, POST.class);
            else if (session.getMethod() == Method.PUT)
                methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, PUT.class);
            else if (session.getMethod() == Method.DELETE)
                methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, DELETE.class);

            //Если метод нашли
            if (methodInfo != null) {
                //Получаем тип ответа
                String produces = methodInfo.getProduces();
                if (produces != null)
                    responseInfo.setMimeType(produces);

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
                        responseInfo.setData(converter.writeValueAsBytes(result));
                }

                //Отправляем ответ
                return responseInfo;
            }
        } catch (Throwable throwable) {
            ReflectionUtils.MethodInfo methodInfo = ReflectionUtils.getDeclaredMethodInfo(controller, ExceptionHandler.class);
            if (methodInfo != null) {
                //Получаем тип ответа
                String produces = methodInfo.getProduces();
                if (produces != null)
                    responseInfo.setMimeType(produces);

                try {
                    methodInfo.invoke(throwable, responseInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return responseInfo;
        }

        //Отвечаем 404
        return newFixedLengthResponse(ResponseStatus.NOT_FOUND, ResponseType.TEXT_PLAIN, ResponseStatus.NOT_FOUND.getDescription());
    }
}
