package com.inkserve.dto;

import jakarta.validation.constraints.NotBlank;

public class RecognizeRequest {
    @NotBlank
    private String imageBase64;

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }
}
