# Formats used by Camelot
This page explains various formats used by Camelot when displaying or parsing inputs (i.e. durations).

## Durations

Durations are composed of sequences of numbers and unit specifiers without a space in between. The number will multiply the specified unit.  
Camelot supports the following units:
- `n`: nanoseconds
- `l`: milliseconds (1000000 nanoseconds)
- `s`: seconds (1000 milliseconds)
- `m`: minutes (60 seconds)
- `h`: hours (60 minutes)
- `d`: days (24 hours)
- `w`: weeks (7 days)
- `M`: months (30 days)
- `y`: years (365 days)

Unit specifiers are **case-sensitive** and unrecognized specifiers will be considered _minutes_.
::: info
While nanoseconds and milliseconds are supported by the parser, most commands will not support that level of precision and will round them to the next second.
:::

If a component of a duration is prefixed by a minus (`-`), it will be subtracted from the duration instead of added.
::: info
The format is not a maths equation. Parenthesis are not supported and the minus only applies to the first component after it.  
`-12m4s` subtracts 12 minutes and *adds* 4 seconds, while `-12m-4s` subtracts 12 minutes and 4 seconds.
:::

### Example durations
- `12m45s`: 12 minutes and 45 seconds
- `2y4M`: 2 years (730 days) and 4 months (120 days); 850 days in total
- `1w2d`: one week and 2 days
- `1d4h25m2s`: 1 day, 4 hours, 25 minutes and 2 seconds
- `2w-4d`: 2 weeks minus 4 days; 10 days in total
