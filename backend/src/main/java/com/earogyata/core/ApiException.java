package com.earogyata.core;
public class ApiException extends RuntimeException {
 public final int status;
 public ApiException(int status,String detail) { super(detail); this.status=status; }
 public static ApiException conflict(String detail) { return new ApiException(409,detail); }
 public static ApiException forbidden() { return new ApiException(403,"This action is not allowed"); }
 public static ApiException missing() { return new ApiException(404,"Resource not found"); }
}
