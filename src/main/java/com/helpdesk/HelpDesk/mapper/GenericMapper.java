package com.helpdesk.HelpDesk.mapper;

public interface GenericMapper<E, D> {
    D toDTO(E entity);
}
