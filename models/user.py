"""User domain model managing demographic profiles, global budgets, and targets."""

from dataclasses import dataclass, field
import re
from typing import Any, Dict
import uuid

EMAIL_REGEX = re.compile(r"^[\w\.-]+@[\w\.-]+\.\w+$")


@dataclass
class User:
    """Represents a user profile with personal targets and financial constraints.

    Attributes:
        id: Unique identifier for the user profile.
        name: Full name of the user.
        email: Primary contact email address.
        currency: Preferred currency code (e.g., 'USD', 'INR', 'EUR').
        monthly_income: Expected regular monthly earnings (>= 0).
        global_budget: High-level monthly spending threshold (>= 0).
        savings_goal: Targeted periodic or milestone savings threshold (>= 0).
    """

    name: str
    email: str
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    currency: str = "USD"
    monthly_income: float = 0.0
    global_budget: float = 0.0
    savings_goal: float = 0.0

    def __post_init__(self) -> None:
        """Validate and normalize user fields post-initialization.

        Raises:
            ValueError: If name is empty, email is malformed, currency is invalid,
                        or any monetary amount is negative.
            TypeError: If monetary fields cannot be converted to numeric floats.
        """
        # ID validation
        if not self.id or not isinstance(self.id, str):
            self.id = str(uuid.uuid4())

        # Name validation
        if not isinstance(self.name, str) or not self.name.strip():
            raise ValueError("User name must be a non-empty string.")
        self.name = self.name.strip()

        # Email validation
        if not isinstance(self.email, str) or not EMAIL_REGEX.match(self.email.strip()):
            raise ValueError(f"Invalid email address provided: '{self.email}'.")
        self.email = self.email.strip().lower()

        # Currency validation
        if not isinstance(self.currency, str) or not self.currency.strip():
            raise ValueError("Currency must be a non-empty string (e.g., 'USD', 'INR').")
        self.currency = self.currency.strip().upper()

        # Validate monetary values (must be >= 0)
        for attr in ("monthly_income", "global_budget", "savings_goal"):
            val = getattr(self, attr)
            try:
                num = float(val)
            except (ValueError, TypeError):
                raise TypeError(f"'{attr}' must be a numeric value.")
            if num < 0:
                raise ValueError(f"'{attr}' cannot be negative. Received: {num}")
            setattr(self, attr, num)

    def __str__(self) -> str:
        """Return a human-readable string representation of the user."""
        return (
            f"User: {self.name} <{self.email}> | Currency: {self.currency} | "
            f"Monthly Income: {self.currency} {self.monthly_income:,.2f} | "
            f"Global Budget: {self.currency} {self.global_budget:,.2f} | "
            f"Savings Goal: {self.currency} {self.savings_goal:,.2f}"
        )

    def to_dict(self) -> Dict[str, Any]:
        """Serialize the user profile into a standard dictionary.

        Returns:
            Dict containing all user profile fields.
        """
        return {
            "id": self.id,
            "name": self.name,
            "email": self.email,
            "currency": self.currency,
            "monthly_income": self.monthly_income,
            "global_budget": self.global_budget,
            "savings_goal": self.savings_goal,
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "User":
        """Instantiate a User object from a dictionary.

        Args:
            data: Dictionary containing user attributes.

        Returns:
            A validated User instance.
        """
        return cls(**data)
