package org.bleachhack.command.exception;

import net.minecraft.text.Text;

import java.io.Serial;

// net.minecraft.command.CommandException was removed entirely, so this now provides its own
// Text-message storage directly instead of inheriting it.
public class CmdSyntaxException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 7940377774005961331L;

	private final Text textMessage;

	public CmdSyntaxException() {
        this("Invalid Syntax!");
    }

    public CmdSyntaxException(String message) {
        this(Text.literal(message));
    }

    public CmdSyntaxException(Text message) {
        super(message.getString());
        this.textMessage = message;
    }

    public Text getTextMessage() {
        return textMessage;
    }

}
