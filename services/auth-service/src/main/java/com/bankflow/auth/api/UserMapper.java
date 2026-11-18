package com.bankflow.auth.api;

import com.bankflow.auth.api.dto.UserResponse;
import com.bankflow.auth.domain.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
