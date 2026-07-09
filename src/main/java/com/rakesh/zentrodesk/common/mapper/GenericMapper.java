package com.rakesh.zentrodesk.common.mapper;

import java.util.List;

public interface GenericMapper<E, D> {

    D toDto(E entity);

    List<D> toDtoList(List<E> entities);
}
