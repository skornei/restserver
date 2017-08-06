package ru.skornei.restserver.server.converter;

public interface BaseConverter {

    /**
     * Преобразовать объект в массив байт
     * @param value объект
     * @return массив байт
     */
    byte[] writeValueAsBytes(Object value);

    /**
     * Получить из массива байт объект
     * @param src массив байт
     * @param valueType тип объекта
     * @param <T>
     * @return объект
     */
    <T> T writeValue(byte[] src, Class<T> valueType);
}
