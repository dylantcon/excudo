package com.excudo.exceptions;

/**
 * Exception thrown when XML parsing operations fail
 */
public class XMLParsingException extends Exception {

  public XMLParsingException(String message) {
    super(message);
  }

  public XMLParsingException(String message, Throwable cause) {
    super(message, cause);
  }

  public XMLParsingException(Throwable cause) {
    super(cause);
  }
}
