"""Transaction domain model representing financial inflow and outflow records."""

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Dict
import uuid

VALID_PAYMENT_MODES = {"Cash", "Card", "UPI", "Bank Transfer", "Wallet"}
VALID_TRANSACTION_TYPES = {"income", "expense"}


@dataclass
class Transaction:
    """Represents a single financial transaction.

    Attributes:
        id: Unique identifier for the transaction.
        timestamp: Date and time when the transaction occurred.
        category: Financial category (e.g., Food, Rent, Salary).
        amount: Monetary value of the transaction (must be strictly > 0).
        payment_mode: Channel used (Cash, Card, UPI, Bank Transfer, Wallet).
        type: Flow direction ('income' or 'expense').
        note: Optional contextual description or remark.
    """

    category: str
    amount: float
    payment_mode: str
    type: str
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    timestamp: datetime = field(default_factory=datetime.now)
    note: str = ""

    def __post_init__(self) -> None:
        """Validate and normalize fields post-initialization.

        Raises:
            ValueError: If amount <= 0, category is empty, payment_mode is invalid,
                        or type is not 'income'/'expense'.
            TypeError: If timestamp or amount cannot be converted to proper types.
        """
        # ID validation
        if not self.id or not isinstance(self.id, str):
            self.id = str(uuid.uuid4())

        # Timestamp validation & normalization
        if isinstance(self.timestamp, str):
            try:
                self.timestamp = datetime.fromisoformat(self.timestamp)
            except ValueError as exc:
                raise ValueError(f"Invalid ISO timestamp format: '{self.timestamp}'") from exc
        elif not isinstance(self.timestamp, datetime):
            raise TypeError("Timestamp must be an instance of datetime or an ISO string.")

        # Category validation
        if not isinstance(self.category, str) or not self.category.strip():
            raise ValueError("Category must be a non-empty string.")
        self.category = self.category.strip()

        # Amount validation
        try:
            self.amount = float(self.amount)
        except (ValueError, TypeError):
            raise TypeError("Amount must be a numeric value.")

        if self.amount <= 0:
            raise ValueError(f"Amount must be strictly greater than 0. Received: {self.amount}")

        # Payment mode validation (case-insensitive lookup with canonical normalization)
        mode_lookup = {mode.lower(): mode for mode in VALID_PAYMENT_MODES}
        if not isinstance(self.payment_mode, str) or self.payment_mode.strip().lower() not in mode_lookup:
            allowed = ", ".join(sorted(VALID_PAYMENT_MODES))
            raise ValueError(
                f"Invalid payment_mode '{self.payment_mode}'. Must be one of: {allowed}"
            )
        self.payment_mode = mode_lookup[self.payment_mode.strip().lower()]

        # Transaction type validation (income / expense)
        if not isinstance(self.type, str) or self.type.strip().lower() not in VALID_TRANSACTION_TYPES:
            raise ValueError(f"Transaction type must be 'income' or 'expense'. Received: '{self.type}'")
        self.type = self.type.strip().lower()

        # Note sanitization
        if self.note is None:
            self.note = ""
        elif not isinstance(self.note, str):
            self.note = str(self.note)
        else:
            self.note = self.note.strip()

    def __str__(self) -> str:
        """Return a human-readable string representation of the transaction."""
        prefix = "+" if self.type == "income" else "-"
        formatted_date = self.timestamp.strftime("%Y-%m-%d %H:%M")
        note_str = f" | Note: {self.note}" if self.note else ""
        return (
            f"[{formatted_date}] {self.type.upper()} ({self.payment_mode}) "
            f"| {self.category}: {prefix}{self.amount:,.2f}{note_str}"
        )

    def to_dict(self) -> Dict[str, Any]:
        """Serialize the transaction object into a standard dictionary.

        Returns:
            Dict containing all transaction fields with timestamp in ISO 8601 format.
        """
        return {
            "id": self.id,
            "timestamp": self.timestamp.isoformat(),
            "category": self.category,
            "amount": self.amount,
            "payment_mode": self.payment_mode,
            "type": self.type,
            "note": self.note,
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "Transaction":
        """Instantiate a Transaction object from a dictionary.

        Args:
            data: Dictionary containing transaction attributes.

        Returns:
            A validated Transaction instance.
        """
        payload = data.copy()
        if "timestamp" in payload and isinstance(payload["timestamp"], str):
            payload["timestamp"] = datetime.fromisoformat(payload["timestamp"])
        return cls(**payload)
