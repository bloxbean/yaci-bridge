"""Data models for the Yaci Python wrapper."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import IntEnum
from typing import Optional


@dataclass
class PeerAddress:
    """A peer address discovered via the PeerSharing mini-protocol."""
    type: str = ""
    address: str = ""
    port: int = 0

    @classmethod
    def _from_dict(cls, d: dict) -> PeerAddress:
        if d is None:
            return cls()
        return cls(
            type=d.get("type", ""),
            address=d.get("address", ""),
            port=d.get("port", 0),
        )


class NetworkType(IntEnum):
    """Cardano network types with their protocol magic numbers."""
    MAINNET = 764824073
    PREPROD = 1
    PREVIEW = 2


@dataclass
class Point:
    """A point on the Cardano blockchain identified by slot and block hash."""
    slot: int
    hash: str


@dataclass
class Tip:
    """The current tip of the Cardano blockchain."""
    slot: int
    hash: str
    block: int


# Well-known points for each network (from Yaci Constants class)
WELL_KNOWN_POINTS = {
    NetworkType.MAINNET: Point(
        slot=16588737,
        hash="4e9bbbb67e3ae262133d94c3da5bffce7b1127fc436e7433b87668dba34c354a"
    ),
    NetworkType.PREPROD: Point(
        slot=87480,
        hash="528c3e6a00c82dd5331b116103b6e427acf447891ce3ade6c4c7a61d2f0a2b1c"
    ),
    NetworkType.PREVIEW: Point(
        slot=8000,
        hash="70da683c00985e23903da00656fae96644e1f31dce914aab4ed50e35e4c4842d"
    ),
}


# ---------------------------------------------------------------------------
# Typed transaction models — constructed from the JSON wire format
# ---------------------------------------------------------------------------

@dataclass
class Amount:
    """A native asset amount (ADA or multi-asset)."""
    unit: str = ""
    policy_id: Optional[str] = None
    asset_name: Optional[str] = None
    quantity: int = 0

    @classmethod
    def _from_dict(cls, d: dict) -> Amount:
        if d is None:
            return cls()
        qty = d.get("quantity", 0)
        # BigInteger serializes as a number in JSON
        if isinstance(qty, str):
            qty = int(qty)
        return cls(
            unit=d.get("unit", ""),
            policy_id=d.get("policyId"),
            asset_name=d.get("assetName"),
            quantity=int(qty) if qty is not None else 0,
        )


@dataclass
class TransactionInput:
    """A transaction input reference."""
    transaction_id: str = ""
    index: int = 0

    @classmethod
    def _from_dict(cls, d: dict) -> TransactionInput:
        if d is None:
            return cls()
        return cls(
            transaction_id=d.get("transactionId", ""),
            index=d.get("index", 0),
        )


@dataclass
class TransactionOutput:
    """A transaction output."""
    address: str = ""
    amounts: list[Amount] = field(default_factory=list)
    datum_hash: Optional[str] = None
    inline_datum: Optional[str] = None
    script_ref: Optional[str] = None

    @classmethod
    def _from_dict(cls, d: dict) -> TransactionOutput:
        if d is None:
            return cls()
        return cls(
            address=d.get("address", ""),
            amounts=[Amount._from_dict(a) for a in d.get("amounts", [])],
            datum_hash=d.get("datumHash"),
            inline_datum=d.get("inlineDatum"),
            script_ref=d.get("scriptRef"),
        )


@dataclass
class Utxo:
    """A resolved UTXO (transaction output with reference back to its tx)."""
    tx_hash: str = ""
    index: int = 0
    address: str = ""
    amounts: list[Amount] = field(default_factory=list)
    datum_hash: Optional[str] = None
    inline_datum: Optional[str] = None
    script_ref: Optional[str] = None

    @classmethod
    def _from_dict(cls, d: dict) -> Utxo:
        if d is None:
            return cls()
        return cls(
            tx_hash=d.get("txHash", ""),
            index=d.get("index", 0),
            address=d.get("address", ""),
            amounts=[Amount._from_dict(a) for a in d.get("amounts", [])],
            datum_hash=d.get("datumHash"),
            inline_datum=d.get("inlineDatum"),
            script_ref=d.get("scriptRef"),
        )


@dataclass
class TransactionBody:
    """The body of a Cardano transaction."""
    tx_hash: str = ""
    cbor: Optional[str] = None
    inputs: list[TransactionInput] = field(default_factory=list)
    outputs: list[TransactionOutput] = field(default_factory=list)
    fee: int = 0
    ttl: int = 0
    validity_interval_start: int = 0
    mint: list[Amount] = field(default_factory=list)
    collateral_inputs: list[TransactionInput] = field(default_factory=list)
    required_signers: list[str] = field(default_factory=list)
    collateral_return: Optional[TransactionOutput] = None
    total_collateral: Optional[int] = None
    reference_inputs: list[TransactionInput] = field(default_factory=list)
    auxiliary_data_hash: Optional[str] = None
    script_data_hash: Optional[str] = None
    network_id: int = 0
    current_treasury_value: Optional[int] = None
    donation: Optional[int] = None
    update: Optional[dict] = None
    # Complex polymorphic structures kept as raw dicts/lists
    certificates: Optional[list] = None
    withdrawals: Optional[dict] = None
    voting_procedures: Optional[dict] = None
    proposal_procedures: Optional[list] = None

    @classmethod
    def _from_dict(cls, d: dict) -> TransactionBody:
        if d is None:
            return cls()

        fee = d.get("fee", 0)
        if isinstance(fee, str):
            fee = int(fee)

        total_col = d.get("totalCollateral")
        if total_col is not None:
            total_col = int(total_col)

        treasury = d.get("currentTreasuryValue")
        if treasury is not None:
            treasury = int(treasury)

        donation = d.get("donation")
        if donation is not None:
            donation = int(donation)

        col_return = d.get("collateralReturn")

        return cls(
            tx_hash=d.get("txHash", ""),
            cbor=d.get("cbor"),
            inputs=[TransactionInput._from_dict(i) for i in d.get("inputs", [])],
            outputs=[TransactionOutput._from_dict(o) for o in d.get("outputs", [])],
            fee=int(fee) if fee is not None else 0,
            ttl=d.get("ttl", 0),
            validity_interval_start=d.get("validityIntervalStart", 0),
            mint=[Amount._from_dict(m) for m in d.get("mint", [])],
            collateral_inputs=[
                TransactionInput._from_dict(i) for i in d.get("collateralInputs", [])
            ],
            required_signers=list(d.get("requiredSigners", [])),
            collateral_return=TransactionOutput._from_dict(col_return) if col_return else None,
            total_collateral=total_col,
            reference_inputs=[
                TransactionInput._from_dict(i) for i in d.get("referenceInputs", [])
            ],
            auxiliary_data_hash=d.get("auxiliaryDataHash"),
            script_data_hash=d.get("scriptDataHash"),
            network_id=d.get("netowrkId", 0),  # Note: typo matches Java getter
            current_treasury_value=treasury,
            donation=donation,
            update=d.get("update"),
            certificates=d.get("certificates"),
            withdrawals=d.get("withdrawals"),
            voting_procedures=d.get("votingProcedures"),
            proposal_procedures=d.get("proposalProcedures"),
        )


@dataclass
class TransactionInfo:
    """A full Cardano transaction with body, UTXOs, witnesses, and metadata."""
    tx_hash: str = ""
    block_number: int = 0
    slot: int = 0
    invalid: bool = False
    body: Optional[TransactionBody] = None
    utxos: list[Utxo] = field(default_factory=list)
    collateral_return_utxo: Optional[Utxo] = None
    witnesses: Optional[dict] = None
    aux_data: Optional[dict] = None

    @classmethod
    def _from_dict(cls, d: dict) -> TransactionInfo:
        if d is None:
            return cls()

        body = d.get("body")
        col_utxo = d.get("collateralReturnUtxo")

        return cls(
            tx_hash=d.get("txHash", ""),
            block_number=d.get("blockNumber", 0),
            slot=d.get("slot", 0),
            invalid=d.get("invalid", False),
            body=TransactionBody._from_dict(body) if body else None,
            utxos=[Utxo._from_dict(u) for u in d.get("utxos", [])],
            collateral_return_utxo=Utxo._from_dict(col_utxo) if col_utxo else None,
            witnesses=d.get("witnesses"),
            aux_data=d.get("auxData"),
        )


@dataclass
class BlockInfo:
    """A Cardano block with typed transaction data."""
    era: str = ""
    slot: int = 0
    hash: str = ""
    block_number: int = 0
    block_cbor: Optional[str] = None
    transactions: list[TransactionInfo] = field(default_factory=list)

    @classmethod
    def _from_dict(cls, d: dict) -> BlockInfo:
        if d is None:
            return cls()
        return cls(
            era=d.get("era", ""),
            slot=d.get("slot", 0),
            hash=d.get("hash", ""),
            block_number=d.get("blockNumber", 0),
            block_cbor=d.get("blockCbor"),
            transactions=[
                TransactionInfo._from_dict(tx) for tx in d.get("transactions", [])
            ],
        )
