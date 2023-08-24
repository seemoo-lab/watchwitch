package net.rec0de.android.watchwitch.servicehandlers.health

import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoBuf
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoI64
import net.rec0de.android.watchwitch.decoders.protobuf.ProtoLen
import net.rec0de.android.watchwitch.hex
import java.util.Date
import java.util.UUID

interface NanoSyncEntity {

    companion object {
        // from HDCodableNanoSyncChange::objectTypeAsString:
        private val objectTypes = mapOf(
            0x1 to "CategorySamples",               // Sample > Data
            0x2 to "QuantitySamples",               // Sample > Data
            0x3 to "Workouts",                      // Sample > Data
            0x4 to "ActivityCaches",                // Sample > Data
            0x5 to "LegacyAchievements",            // dead
            0x6 to "UserCharacteristics",           // KeyValue >
            0x7 to "Deprecated7",                   // dead
            0x8 to "ObjectAssociations",            // root class
            0x9 to "UnitPreferences",               // KeyValue >
            0xa to "Sources",                       // root class
            0xb to "Authorizations",                // root class
            0xc to "Devices",                       // root class
            0xd to "Correlations",                  // Sample > Data
            0xe to "DataTypeSourceOrder",           // root class
            0xf to "MedicalID",                     // root class
            0x10 to "NanoUserDefaults",             // KeyValue >
            0x11 to "ProtectedNanoUserDefaults",    // NanoUserDefaults > KeyValue >
            0x12 to "LocationSeriesSamples",        // Sample > Data
            0x13 to "DeletedSamples",               // Sample > Data
            0x14 to "LegacyAchievementKeyValue",    // dead
            0x15 to "ActivityAchievementsKeyValue", // dead?
            0x16 to "BinarySamples",                // Sample > Data
            0x17 to "CDADocumentSamples",
            0x18 to "FHIRResources",
            0x1a to "ClinicalGateways",
            0x1d to "MedicationOrders",
            0x1e to "MedicationDispenseRecords",
            0x1f to "MedicationRecords",
            0x20 to "DiagnosticTestResults",
            0x21 to "DiagnosticTestReports",
            0x22 to "VaccinationRecords",
            0x23 to "ConditionRecords",
            0x24 to "AllergyRecords",
            0x25 to "ProcedureRecords",
            0x28 to "ClinicalAccounts",
            0x29 to "UserDefaults",
            0x2a to "ClinicalDeletedAccounts",
            0x2b to "AccountOwners",
            0x2c to "UnknownRecords"
        )

        fun objTypeToString(objectType: Int): String {
            return if (objectTypes.containsKey(objectType))
                objectTypes[objectType]!!
            else
                "Unknown($objectType)"
        }

        fun fromPB(pb: ProtoBuf?, type: Int?): NanoSyncEntity? {
            if(pb == null || type == null)
                return null
            return fromSafePB(pb, type)
        }

        fun fromSafePB(pb: ProtoBuf, type: Int): NanoSyncEntity {
            return when (type) {
                0x1, 0x2, 0x3, 0x4, 0xd, 0x12, 0x13, 0x16 -> ObjectCollection.fromSafePB(pb)
                0x6, 0x9, 0x10, 0x11 -> CategoryDomainDictionary.fromSafePB(pb)
                0x8 -> ObjectAssociation.fromSafePB(pb)
                0xa -> Source.fromSafePB(pb)
                0xb -> SourceAuthorization.fromSafePB(pb)
                0xc -> Device.fromSafePB(pb)
                0xe -> ObjectTypeSourceOrder.fromSafePB(pb)
                0xf -> MedicalIDData.fromSafePB(pb)
                else -> throw Exception("Unsupported NanoSyncEntity ${objTypeToString(type)}")
            }
        }
    }
}


class ObjectCollection(
    val sourceBundleIdentifier: String?,
    val source: Source?,
    val categorySample: CategorySample?,
    val quantitySample: QuantitySample?,
    val workout: Workout?,
    val deletedSample: DeletedSample?,
    val provenance: Provenance?
) : NanoSyncEntity {
    companion object : PBParsable<ObjectCollection>() {
        override fun fromSafePB(pb: ProtoBuf): ObjectCollection {
            println(pb)
            val sourceBundle = pb.readOptString(1)
            val source = Source.fromPB(pb.readOptionalSinglet(2) as ProtoBuf?)
            val categorySample = CategorySample.fromPB(pb.readOptionalSinglet(3) as ProtoBuf?)
            val quantitySample = QuantitySample.fromPB(pb.readOptionalSinglet(4) as ProtoBuf?)
            val workout = Workout.fromPB(pb.readOptionalSinglet(5) as ProtoBuf?)

            val deletedSample = DeletedSample.fromPB(pb.readOptionalSinglet(9) as ProtoBuf?)
            val provenance = Provenance.fromPB(pb.readOptionalSinglet(20) as ProtoBuf?)

            return ObjectCollection(sourceBundle, source, categorySample, quantitySample, workout, deletedSample, provenance)
        }
    }

    override fun toString(): String {
        val containedObjects = mutableListOf<String>()

        if(categorySample != null)
            containedObjects.add(categorySample.toString())
        if(quantitySample != null)
            containedObjects.add(quantitySample.toString())
        if(workout != null)
            containedObjects.add(workout.toString())
        if(deletedSample != null)
            containedObjects.add(deletedSample.toString())

        return "ObjectCollection(sourceBundle $sourceBundleIdentifier, source $source, provenance $provenance, $containedObjects)"
    }
}

class CategoryDomainDictionary(
    val domain: String?,
    val category: Int?,
    val keyValuePairs: List<TimestampedKeyValuePair>
) : NanoSyncEntity {
    companion object : PBParsable<CategoryDomainDictionary>() {
        override fun fromSafePB(pb: ProtoBuf): CategoryDomainDictionary {
            val entries = pb.readMulti(3).map { TimestampedKeyValuePair.fromSafePB(it as ProtoBuf) }
            val domain = pb.readOptString(2)
            val category = pb.readOptShortVarInt(1)

            return CategoryDomainDictionary(domain, category, entries)
        }
    }
}

class MedicalIDData(val medicalIDBytes: ByteArray?) : NanoSyncEntity {
    companion object : PBParsable<MedicalIDData>() {
        override fun fromSafePB(pb: ProtoBuf): MedicalIDData {
            val bytes = (pb.readOptionalSinglet(1) as ProtoLen?)?.value
            return MedicalIDData(bytes)
        }
    }

    override fun toString() = "MedicalIDData(${medicalIDBytes?.hex()})"
}

class ObjectTypeSourceOrder(
    val objectType: Int?,
    val orderUsed: Int?,
    val sourceUUIDs: List<UUID>,
    val modificationDates: List<Date>
) : NanoSyncEntity {
    companion object : PBParsable<ObjectTypeSourceOrder>() {
        override fun fromSafePB(pb: ProtoBuf): ObjectTypeSourceOrder {
            val objectType = pb.readOptShortVarInt(1)
            val orderUsed = pb.readOptShortVarInt(2)
            val sourceUUIDs = pb.readMulti(3).map { Utils.uuidFromBytes((it as ProtoLen).value) }
            val modificationDates = pb.readMulti(4).map { (it as ProtoI64).asDate() }

            return ObjectTypeSourceOrder(objectType, orderUsed, sourceUUIDs, modificationDates)
        }
    }

    override fun toString() = "ObjectTypeSourceOrder(objType $objectType, orderUsed $orderUsed, sourceUUIDs: $sourceUUIDs, modificationDates: $modificationDates)"
}

class Device(
    val name: String?,
    val manufacturer: String?,
    val model: String?,
    val hardwareVersion: String?,
    val firmwareVersion: String?,
    val softwareVersion: String?,
    val localIdentifier: String?,
    val fDAUDI: String?,
    val uuid: UUID?,
    val creationDate: Date?
) : NanoSyncEntity {
    companion object : PBParsable<Device>() {
        override fun fromSafePB(pb: ProtoBuf): Device {
            val name = pb.readOptString(1)
            val manufacturer = pb.readOptString(2)
            val model = pb.readOptString(3)
            val hardwareVersion = pb.readOptString(4)
            val firmwareVersion = pb.readOptString(5)
            val softwareVersion = pb.readOptString(6)
            val localIdentifier = pb.readOptString(7)
            val fDAUDI = pb.readOptString(8) // what is this?
            val uuidBytes = (pb.readOptionalSinglet(9) as ProtoLen?)?.value
            val uuid = if(uuidBytes == null) null else Utils.uuidFromBytes(uuidBytes)
            val creationDate = pb.readOptDate(10)

            return Device(name, manufacturer, model, hardwareVersion, firmwareVersion, softwareVersion, localIdentifier, fDAUDI, uuid, creationDate)
        }
    }

    override fun toString() = "Device($name, $manufacturer, $model, hard: $hardwareVersion, firm: $firmwareVersion, soft: $softwareVersion, localID: $localIdentifier, fDAUDI: $fDAUDI, uuid: $uuid, created $creationDate)"
}

class SourceAuthorization(
    val sourceUUID: UUID,
    val authorization: Authorization?,
    val backupUUID: UUID?,
    val source: Source?
) : NanoSyncEntity {
    companion object : PBParsable<SourceAuthorization>() {
        override fun fromSafePB(pb: ProtoBuf): SourceAuthorization {
            val sourceUUID = Utils.uuidFromBytes((pb.readAssertedSinglet(1) as ProtoLen).value)
            val authorization = Authorization.fromPB(pb.readOptionalSinglet(2) as ProtoBuf?)
            val backupUUIDBytes = (pb.readOptionalSinglet(3) as ProtoLen?)?.value
            val backupUUID = if(backupUUIDBytes == null) null else Utils.uuidFromBytes(backupUUIDBytes)
            val source = Source.fromPB(pb.readOptionalSinglet(4) as ProtoBuf?)
            return SourceAuthorization(sourceUUID, authorization, backupUUID, source)
        }

    }
}

class ObjectAssociation(
    val associationUUID: UUID,
    val objectUUIDs: List<UUID>
) : NanoSyncEntity {
    companion object : PBParsable<ObjectAssociation>() {
        override fun fromSafePB(pb: ProtoBuf): ObjectAssociation {
            val associationUUID = Utils.uuidFromBytes((pb.readAssertedSinglet(1) as ProtoLen).value)
            val objectUUIDs = pb.readMulti(2).map { Utils.uuidFromBytes((it as ProtoLen).value) }
            return ObjectAssociation(associationUUID, objectUUIDs)
        }
    }

    override fun toString(): String {
        return "ObjectAssociation(uuid: $associationUUID, objects: $objectUUIDs)"
    }
}

class Source(
    val name: String?,
    val bundleIdentifier: String?,
    val productType: String?,
    val options: Long?,
    val uuid: UUID,
    val modificationDate: Date?,
    val deleted: Boolean?,
    val owningAppBundleIdentifier: String?
) : NanoSyncEntity {
    companion object : PBParsable<Source>() {
        override fun fromSafePB(pb: ProtoBuf): Source {
            val name = pb.readOptString(1)
            val bundle = pb.readOptString(2)
            val product = pb.readOptString(3)
            val options = pb.readOptLongVarInt(4)
            val uuid = Utils.uuidFromBytes((pb.readAssertedSinglet(5) as ProtoLen).value)
            val modified = (pb.readOptionalSinglet(6) as ProtoI64?)?.asDate()
            val deleted = pb.readOptBool(7)
            val owningBundle = pb.readOptString(8)

            return Source(name, bundle, product, options, uuid, modified, deleted, owningBundle)
        }
    }

    override fun toString(): String {
        return "Source($name, $bundleIdentifier, $productType, options $options, uuid $uuid, deleted? $deleted, modified $modificationDate, owningBundle $owningAppBundleIdentifier)"
    }
}