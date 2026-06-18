package br.com.vanroute.backend.validators.password;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Field;

public class PasswordMatchValidator 
        implements ConstraintValidator<PasswordMatch, Object> {


    @Override
    public boolean isValid(
            Object object,
            ConstraintValidatorContext context
    ) {

        if(object == null){
            return true;
        }


        try {

            Field password =
                object.getClass().getDeclaredField("password");

            Field confirmPassword =
                object.getClass().getDeclaredField("confirmPassword");


            password.setAccessible(true);
            confirmPassword.setAccessible(true);


            Object passwordValue =
                password.get(object);

            Object confirmPasswordValue =
                confirmPassword.get(object);


            return passwordValue != null 
                && passwordValue.equals(confirmPasswordValue);


        } catch (Exception e) {

            return false;
        }
    }
}