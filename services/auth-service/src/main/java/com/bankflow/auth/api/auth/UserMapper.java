package com.bankflow.auth.api.auth;

import com.bankflow.auth.api.auth.dto.UserResponse;
import com.bankflow.auth.domain.user.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse mapToResponse(User user);
}
