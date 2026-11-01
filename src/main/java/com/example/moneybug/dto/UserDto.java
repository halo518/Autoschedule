package com.example.moneybug.dto;

import com.example.moneybug.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class UserDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        @NotBlank
        @Size(max = 100)
        private String kakaoId;

        @NotNull
        private User.Role role;

        @NotBlank
        @Size(max = 50)
        private String name;

        @NotBlank
        @Size(max = 20)
        private String contact;

        public User toEntity() {
            User user = new User();
            user.setKakaoId(kakaoId);
            user.setRole(role);
            user.setName(name);
            user.setContact(contact);
            return user;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Integer id;
        private String kakaoId;
        private User.Role role;
        private String name;
        private String contact;

        public static Response from(User user) {
            return Response.builder()
                    .id(user.getId())
                    .kakaoId(user.getKakaoId())
                    .role(user.getRole())
                    .name(user.getName())
                    .contact(user.getContact())
                    .build();
        }
    }
}
