# Store the initial ZDOTDIR value
WADDLE_ZDOTDIR="$ZDOTDIR"

# Source the original zshenv
[ -f ~/.zshenv ] && source ~/.zshenv

# Detect if ZDOTDIR has changed
if [ "$ZDOTDIR" != "$WADDLE_ZDOTDIR" ]; then
  # If changed, manually source your custom zshrc from the original WADDLE_ZDOTDIR
  [ -f "$WADDLE_ZDOTDIR/.zshrc" ] && source "$WADDLE_ZDOTDIR/.zshrc"
fi