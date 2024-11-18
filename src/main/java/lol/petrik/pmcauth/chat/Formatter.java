package lol.petrik.pmcauth.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class Formatter {
  @SuppressWarnings("SameParameterValue")
  private static Component wrapWithBrackets(Component input) {
    Component leftBracket = Component.text("[")
        .color(NamedTextColor.WHITE)
        .decorate(TextDecoration.BOLD);
    Component rightBracket = Component.text("]")
        .color(NamedTextColor.WHITE)
        .decorate(TextDecoration.BOLD);
    return leftBracket.append(input).append(rightBracket).appendSpace();
  }

  private static final Component BRAND = Component.text("PetrikMC")
      .color(NamedTextColor.GREEN)
      .decorate(TextDecoration.BOLD);
  private static final Component PREFIX = wrapWithBrackets(BRAND);

  public static Component info(String message) {
    return PREFIX.append(Component.text(message).color(NamedTextColor.WHITE).decorate());
  }

  public static Component warning(String message) {
    return PREFIX.append(Component.text(message).color(NamedTextColor.YELLOW));
  }

  public static Component error(String message) {
    return PREFIX.append(Component.text(message).color(NamedTextColor.RED));
  }
}